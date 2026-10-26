package com.alkadev.utils.objectmanager;
/*
 * @author : lejla
 * @since : 6/2/2021, Wed
 **/

import com.alkadev.utils.common.AlkaComponent;
import com.alkadev.utils.common.PluginLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A manager: owns a resource (a connection, a map, a service) and exposes it to plugins through {@link #process}.
 * The settings are in {@code properties}, the instance name in {@code managerName}; both are set before init(), never in the constructor.
 */
public abstract class ObjectManager extends AlkaComponent {
    private static final Logger logger = LogManager.getLogger(ObjectManager.class);

    protected String managerName;
    protected ObjectManagerType objectManagerType;

    // numbers the engine writes to the status file (the shared ones are in AlkaComponent)
    private final AtomicLong syncCalls = new AtomicLong();
    private final AtomicLong asyncCalls = new AtomicLong();

    public ObjectManager(ObjectManagerType objectManagerType) {
        this.objectManagerType = objectManagerType;
    }

    /** Called once, before run(). Return true when the manager is ready; false (or an exception) says it could not start: the engine marks it failed, skips it and never calls its run() or stop(). */
    public abstract boolean init();

    /** Called once, after init() of every instance. false (or an exception) marks the manager failed; its stop() is still called, to let it release what init() opened. */
    public abstract boolean run();

    /** Called once when the service stops, only for an instance whose init() returned true. A false result is logged as a warning. */
    public abstract boolean stop();

    /** Runs on up to nThread pool threads at once (processAsync) plus any caller thread (processSync): must be thread-safe. */
    public abstract Object process(Object o);

    @Override
    protected final void onAttached() {
        managerName = instanceName();
    }

    @Override
    protected final String kindLabel() {
        return "Manager";
    }

    @Override
    protected final String poolPrefix() {
        return "ObjectManager-" + objectManagerType;
    }

    @Override
    protected final void addCounters(Map<String, Object> out) {
        out.put("syncCalls", syncCalls.get());
        out.put("asyncCalls", asyncCalls.get());
    }

    public String getManagerName() {
        return managerName;
    }

    public ObjectManagerType getObjectManagerType() {
        return objectManagerType;
    }

    /**
     * Loads a manager from its jar (see {@link PluginLoader}); null when it could not be loaded.
     */
    public static ObjectManager loadManager(Properties serviceSettings, String configFolder, String commonConfigFolder, String pluginFolder,
                                            String sourceName, String jarFileNameToLoad) {
        ObjectManager manager = PluginLoader.load(ObjectManager.class, "Manager", "managers", serviceSettings, configFolder, commonConfigFolder, pluginFolder,
                sourceName, jarFileNameToLoad);
        // the type the jar declares in its settings template is the type its constructor gives: a plugin is wired by the declared one
        if (manager != null && !manager.declaredManagerType().isEmpty() && !manager.declaredManagerType().equals(String.valueOf(manager.getObjectManagerType())))
            logger.warn("Manager [" + sourceName + "] declares the type " + manager.declaredManagerType() + " in its settings template, but its constructor says " + manager.getObjectManagerType() + ".");
        return manager;
    }

    public Object processSync(Object object) {
        if (!accepting()) {
            noteDropped();
            throw new IllegalStateException("Manager [" + managerName + "] is " + lifecycle() + ", the call was not made.");
        }
        syncCalls.incrementAndGet();
        noteActivity();
        try {
            return process(object);
        } catch (RuntimeException | Error e) {
            noteFailure(e);
            throw e;
        }
    }

    public void processAsync(Object object) {
        if (!accepting()) {
            noteDropped();
            return;
        }
        ExecutorService ex = getExecutor();
        if (ex == null) {
            logger.warn("Manager [" + managerName + "] is shut down, async request dropped.");
            return;
        }

        Callable<Boolean> taskExecute = () -> {
            noteActivity();
            try {
                process(object);
            } catch (Throwable e) {
                // a pool swallows whatever a task throws: it is counted and logged here, or nobody would ever know
                noteFailure(e);
                logger.error("Manager [" + managerName + "] process() failed during async dispatch.", e);
            }
            return true;
        };

        try {
            ex.submit(taskExecute);
            asyncCalls.incrementAndGet();
        } catch (RejectedExecutionException e) {
            logger.warn("Manager [" + managerName + "] is shutting down, async request dropped.");
        }
    }
}
