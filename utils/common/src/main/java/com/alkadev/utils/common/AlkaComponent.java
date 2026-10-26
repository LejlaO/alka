package com.alkadev.utils.common;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a plugin and a manager have in common: the settings, the async pool, the log helpers and the numbers the engine writes to the status file.
 * Plugin and manager authors do not extend this class: they extend {@code ObjectPlugin} or {@code ObjectManager}.
 */
public abstract class AlkaComponent {
    private static final Logger logger = LogManager.getLogger(AlkaComponent.class);
    private static final long EXECUTOR_SHUTDOWN_WAIT_SECONDS = 5;

    /** The settings of this instance (service config, then common file, then the instance's own file); set before init(), never in the constructor. */
    protected Properties properties;
    // the pool of an instance runs what the instance SENDS asynchronously: a plugin's async events to its receivers (their accept() runs on the pool of the sender),
    // and a manager's processAsync() calls. So nThread and maxQueued of a slow receiver do not limit the events it receives; the sender's do. When the sender's
    // queue is full the thread that sends runs the receiver's accept() itself, and the numbers of the pool (queued, active, longest task) are the sender's.
    protected int nThread = 5;
    protected int maxQueued = 10_000;
    protected int slowTaskSeconds = 30;

    private volatile String instanceName = "";
    // log lines of this instance: the name goes first in brackets, so the Studio files the line under this box (see InstanceLog)
    private Logger instanceLogger;

    private final Object executorLock = new Object();
    private volatile ExecutorService executor;
    private boolean executorShutdown;

    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile String lifecycle = "new";
    private volatile long lastActivity = System.currentTimeMillis();
    private volatile String lastFailure = "";

    // the three calls of the life of an instance; the engine drives plugins and managers through them with one piece of code. ObjectPlugin and ObjectManager repeat them with the rules for authors.
    public abstract boolean init();

    public abstract boolean run();

    public abstract boolean stop();

    /** "Plugin" or "Manager": used in log lines. */
    protected abstract String kindLabel();

    /** The name prefix of the pool's threads. */
    protected abstract String poolPrefix();

    /** Adds the numbers that differ between kinds (sent and received, or sync and async calls) to the status snapshot. */
    protected abstract void addCounters(Map<String, Object> out);

    /** Called once the engine has set the settings and the name, before init(). */
    protected void onAttached() {
    }

    // what the jar says about itself in its settings template: the manager type it is (a manager), and the manager types it asks for (a plugin)
    private volatile String declaredManagerType = "";
    private volatile List<String> declaredUses = List.of();

    final void declare(String managerType, List<String> uses) {
        declaredManagerType = managerType == null ? "" : managerType;
        declaredUses = uses == null ? List.of() : List.copyOf(uses);
    }

    /** The manager type the settings template of the jar declares for this manager, "" when it does not say. */
    public final String declaredManagerType() {
        return declaredManagerType;
    }

    /** The manager types the settings template of the jar declares that this plugin uses, empty when it does not say. */
    public final List<String> declaredUsesManagerTypes() {
        return declaredUses;
    }

    final void attach(Properties settings, String name) {
        properties = settings;
        instanceName = name;
        onAttached();
    }

    protected final String instanceName() {
        return instanceName;
    }

    protected final void logDebug(String message, Object... args) {
        log(Level.DEBUG, message, args);
    }

    protected final void logInfo(String message, Object... args) {
        log(Level.INFO, message, args);
    }

    protected final void logWarn(String message, Object... args) {
        log(Level.WARN, message, args);
    }

    protected final void logError(String message, Object... args) {
        log(Level.ERROR, message, args);
    }

    private void log(Level level, String message, Object[] args) {
        Logger l = instanceLogger;
        if (l == null) instanceLogger = l = LogManager.getLogger(getClass());
        InstanceLog.log(l, level, instanceName, message, args);
    }

    /** Where the instance is in its life: new, initialized, running, stopped (the service was stopped) or failed (init() or run() failed). Set by the engine. */
    public final String lifecycle() {
        return lifecycle;
    }

    public final void lifecycle(String state) {
        lifecycle = state;
    }

    /** False while the instance is stopped or failed: events and calls that arrive then are dropped and counted, not delivered. */
    protected final boolean accepting() {
        String state = lifecycle;
        return !state.equals("stopped") && !state.equals("failed");
    }

    protected final void noteDropped() {
        dropped.incrementAndGet();
    }

    protected final void noteActivity() {
        lastActivity = System.currentTimeMillis();
    }

    protected final void noteFailure(Throwable e) {
        failures.incrementAndGet();
        lastFailure = e.toString();
    }

    /** What the status file reports about this instance. */
    public final Map<String, Object> statusSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kindLabel().toLowerCase());
        out.put("state", lifecycle);
        addCounters(out);
        out.put("errors", failures.get());
        out.put("dropped", dropped.get());
        out.putAll(AsyncPools.stats(executor));
        out.put("idleSeconds", (System.currentTimeMillis() - lastActivity) / 1000);
        out.put("lastError", lastFailure);
        return out;
    }

    // lazy, not built in the constructor: a subclass may change nThread in its own constructor; null once the pool was shut down
    protected final ExecutorService getExecutor() {
        ExecutorService ex = executor;
        if (ex == null) {
            synchronized (executorLock) {
                if (executorShutdown) return null;
                ex = executor;
                if (ex == null) {
                    ex = AsyncPools.create(nThread, maxQueued, slowTaskSeconds, poolPrefix(), kindLabel() + " [" + instanceName + "]", logger);
                    executor = ex;
                }
            }
        }
        return ex;
    }

    // engine calls this after stop(): drains queued work for 5s, then forces; permanent
    public final void shutdownExecutor() {
        ExecutorService ex;
        synchronized (executorLock) {
            executorShutdown = true;
            ex = executor;
            executor = null;
        }
        if (ex == null) return;

        ex.shutdown();
        try {
            if (!ex.awaitTermination(EXECUTOR_SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn(kindLabel() + " [" + instanceName + "] async work did not finish within "
                        + EXECUTOR_SHUTDOWN_WAIT_SECONDS + "s, forcing shutdown.");
                ex.shutdownNow();
            }
        } catch (InterruptedException e) {
            ex.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
