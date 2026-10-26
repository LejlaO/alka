package com.alkadev.utils.objectplugin;

import com.alkadev.utils.common.AlkaComponent;
import com.alkadev.utils.common.AlkaException;
import com.alkadev.utils.common.PluginLoader;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A plugin: receives events from other plugins ({@link #accept}), sends events on to the plugins it is wired to, and uses managers.
 * The settings are in {@code properties}, the instance name in {@code pluginName}; both are set before init(), never in the constructor.
 */
public abstract class ObjectPlugin extends AlkaComponent {
    private static final Logger logger = LogManager.getLogger(ObjectPlugin.class);

    protected String pluginName;

    private final ConcurrentSkipListMap<String, ObjectPlugin> receivers = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<ObjectManagerType, ConcurrentSkipListMap<String, ObjectManager>> managers = new ConcurrentHashMap<>();

    // numbers the engine writes to the status file (the shared ones are in AlkaComponent)
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong received = new AtomicLong();

    /** Called once, before run(). Return true when the plugin is ready; false (or an exception) says it could not start: the engine marks it failed, skips it and never calls its run() or stop(). */
    public abstract boolean init();

    /** Called once, after init() of every instance. false (or an exception) marks the plugin failed; its stop() is still called, to let it release what init() opened. */
    public abstract boolean run();

    /** Called once when the service stops, only for an instance whose init() returned true. A false result is logged as a warning. */
    public abstract boolean stop();

    // runs on async pool threads and sync callers at once: must be thread-safe; a sync sender gets the return value
    public abstract Object accept(Object o) throws Exception;

    @Override
    protected final void onAttached() {
        pluginName = instanceName();
    }

    @Override
    protected final String kindLabel() {
        return "Plugin";
    }

    @Override
    protected final String poolPrefix() {
        return "ObjectPlugin";
    }

    @Override
    protected final void addCounters(Map<String, Object> out) {
        out.put("sent", sent.get());
        out.put("received", received.get());
    }

    public void addManager(ObjectManager manager) {
        if (manager == null) return;
        // a plugin that says which manager types it uses is told when it is wired to another type (it is wired anyway: only the plugin knows what it does)
        if (!declaredUsesManagerTypes().isEmpty() && !declaredUsesManagerTypes().contains(String.valueOf(manager.getObjectManagerType())))
            logger.warn("Plugin [" + pluginName + "] is wired to manager [" + manager.getManagerName() + "] of the type " + manager.getObjectManagerType() + ", but its settings template says it uses "
                    + String.join(", ", declaredUsesManagerTypes()) + ".");
        managers.computeIfAbsent(manager.getObjectManagerType(), type -> new ConcurrentSkipListMap<>()).put(manager.getManagerName(), manager);
    }

    public ObjectManager getManager(ObjectManagerType objectManagerType, String managerName) {
        if (objectManagerType == null) return null;
        ConcurrentSkipListMap<String, ObjectManager> ofType = managers.get(objectManagerType);
        if (ofType == null) return null;

        for (ObjectManager manager : ofType.values()) {
            if (managerName == null || manager.getManagerName().equals(managerName))
                return manager;
        }

        return null;
    }

    public void addReceiver(ObjectPlugin receiver) {
        if (receiver == null) return;

        receivers.put(receiver.pluginName, receiver);
    }

    public ObjectPlugin getReceiver(String pluginName) {
        return pluginName == null ? null : receivers.get(pluginName);
    }

    /**
     * Loads a plugin from its jar (see {@link PluginLoader}); null when it could not be loaded.
     */
    public static ObjectPlugin loadPlugin(Properties serviceSettings, String configFolder, String commonConfigFolder, String pluginFolder,
                                          String sourceName, String jarFileNameToLoad) {
        return PluginLoader.load(ObjectPlugin.class, "Plugin", "plugins", serviceSettings, configFolder, commonConfigFolder, pluginFolder,
                sourceName, jarFileNameToLoad);
    }

    // async, fire-and-forget to every receiver
    public void onAsyncEvent(Object object) throws Exception {
        logger.trace("Received object {}", object);

        ExecutorService ex = getExecutor();
        if (ex == null) {
            logger.warn("Plugin [" + pluginName + "] is shut down, async event dropped.");
            return;
        }

        for (ObjectPlugin receiver : receivers.values()) {
            dispatchAsync(ex, receiver, object);
        }
    }

    // every event that reaches a plugin goes through here, so the status file can count it; an Error counts as a failure too
    private Object deliver(ObjectPlugin target, Object object) throws Exception {
        if (!target.accepting()) {
            target.noteDropped();
            return null;
        }
        target.received.incrementAndGet();
        target.noteActivity();
        try {
            return target.accept(object);
        } catch (Exception | Error e) {
            target.noteFailure(e);
            throw e;
        }
    }

    private void dispatchAsync(ExecutorService ex, ObjectPlugin target, Object object) {
        Callable<Boolean> taskExecute = () -> {
            try {
                deliver(target, object);
            } catch (Throwable e) {
                // a pool swallows whatever a task throws: it is logged here, or nobody would ever know
                logger.error("Plugin [" + target.pluginName + "] accept() failed during async dispatch.", e);
            }
            return true;
        };

        try {
            ex.submit(taskExecute);
            sent.incrementAndGet();
        } catch (RejectedExecutionException e) {
            logger.warn("Plugin [" + pluginName + "] is shutting down, async event for [" + target.pluginName + "] dropped.");
        }
    }

    // async to one receiver (null target = all); an unknown target throws
    public void onAsyncEvent(Object object, String targetName) throws Exception {
        if (targetName == null) {
            onAsyncEvent(object);
        } else {
            ObjectPlugin objectPlugin = receivers.get(targetName);
            if (objectPlugin != null) {
                ExecutorService ex = getExecutor();
                if (ex == null) {
                    logger.warn("Plugin [" + pluginName + "] is shut down, async event dropped.");
                    return;
                }
                dispatchAsync(ex, objectPlugin, object);
            } else throw new AlkaException("Invalid target " + targetName + " for " + pluginName);
        }
    }

    // sync to one receiver, returns its accept() result (null target = broadcast, returns null); an unknown target throws
    public Object onSyncEvent(Object object, String targetName) throws Exception {
        Object retVal = null;
        if (targetName == null) {
            onSyncEvent(object);
        } else {
            ObjectPlugin objectPlugin = receivers.get(targetName);
            if (objectPlugin != null) {
                sent.incrementAndGet();
                retVal = deliver(objectPlugin, object);
            } else throw new AlkaException("Invalid target " + targetName + " for " + pluginName);
        }

        return retVal;
    }

    // sync broadcast in name order: every receiver is tried, then the first failure is thrown with the rest suppressed
    public void onSyncEvent(Object object) throws Exception {
        Exception first = null;
        for (ObjectPlugin receiver : receivers.values()) {
            try {
                sent.incrementAndGet();
                deliver(receiver, object);
            } catch (Exception e) {
                if (first == null) first = e;
                else first.addSuppressed(e);
            }
        }
        if (first != null) throw first;
    }
}
