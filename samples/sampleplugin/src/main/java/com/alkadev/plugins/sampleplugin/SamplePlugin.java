package com.alkadev.plugins.sampleplugin;

import com.alkadev.annotations.AlkaSettings;
import com.alkadev.annotations.Setting;
import com.alkadev.annotations.SettingType;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;
import com.alkadev.utils.objectplugin.ObjectPlugin;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample of a Plugin: looks up its Manager once in init(), then drives it from a heartbeat (alternating sync and async calls).
 * <ul>
 * <li>A public no-arg constructor is required: the engine creates the class by reflection from the jar's {@code classname} manifest entry.</li>
 * <li>{@code properties} and {@code pluginName} are set before init(), never in the constructor.</li>
 * <li>The Manager comes from {@code getManager(type, name)} and is kept in a field; which instance is config-driven ({@code sample.managerName}).</li>
 * <li>accept() is called by other Plugins on several threads at once: keep it thread-safe.</li>
 * </ul>
 */
@AlkaSettings(description = "Reference plugin: drives its manager from a heartbeat.", usesManagerTypes = "SAMPLEMANAGER", value = {
        @Setting(key = "sample.managerName", description = "Name of the manager instance this plugin binds to (see alkaprocessor.addmanager in the service config)."),
        @Setting(key = "sample.heartbeatSeconds", type = SettingType.NUMBER, defaultValue = "2", min = "1", max = "3600",
                description = "Seconds between two heartbeat calls to the manager."),
        @Setting(key = "sample.mode", type = SettingType.LIST, options = {"alternate", "sync", "async"}, defaultValue = "alternate",
                description = "Which calls the heartbeat makes: alternate between sync and async, or only one kind."),
        @Setting(key = "sample.logCalls", type = SettingType.BOOLEAN, defaultValue = "true",
                description = "Write a log line for every heartbeat call."),
        @Setting(key = "sample.forward", type = SettingType.BOOLEAN, defaultValue = "false",
                description = "After each heartbeat call, also send the request on to the plugins this one is wired to (shows how a plugin sends to its receivers)."),
        @Setting(key = "sample.messages", defaultValue = "...do iza svemira,|....i nazad!",
                description = "Texts the plugin writes to the log one after another, separated by |. Empty means none."),
        @Setting(key = "sample.messageMillis", type = SettingType.NUMBER, defaultValue = "500", min = "0", max = "3600000",
                description = "Milliseconds between two of those messages. 0 switches them off."),
        @Setting(key = "sample.initDelaySeconds", type = SettingType.NUMBER, defaultValue = "0", min = "0", max = "3600",
                description = "Seconds init() waits before it returns. For trying how the engine copes with a plugin that is slow to start (a stop or a second start while it loads). 0 means no wait."),
        @Setting(key = "sample.initFails", type = SettingType.BOOLEAN, defaultValue = "false",
                description = "Makes init() return false, for trying how the engine treats a plugin that says it could not start."),
        @Setting(key = "sample.runFails", type = SettingType.BOOLEAN, defaultValue = "false",
                description = "Makes run() return false, like sample.initFails but one step later."),
        @Setting(key = "sample.stopDelaySeconds", type = SettingType.NUMBER, defaultValue = "0", min = "0", max = "3600",
                description = "Seconds stop() waits before it returns. For trying how the engine copes with a plugin that is slow to stop. 0 means no wait."),
        @Setting(key = "sample.acceptFailure", type = SettingType.LIST, options = {"none", "exception", "error"}, defaultValue = "none",
                description = "Makes accept() fail on purpose, with an exception or with an Error, to try how the engine reports a plugin that fails.")
})
public class SamplePlugin extends ObjectPlugin {
    private final AtomicInteger tick = new AtomicInteger();
    private final AtomicInteger said = new AtomicInteger();
    private volatile String[] messages = new String[0];
    private volatile String mode = "alternate";
    private volatile boolean logCalls = true;
    private volatile boolean forward;
    private ScheduledExecutorService scheduler;
    private volatile ObjectManager manager;

    @Override
    public boolean init() {
        try {
            int wait = Math.max(0, Integer.parseInt(properties.getProperty("sample.initDelaySeconds", "0").trim()));
            if (wait > 0) {
                logInfo("init() waits {} s on purpose (sample.initDelaySeconds).", wait);
                Thread.sleep(wait * 1000L);
            }
        } catch (NumberFormatException e) {
            logWarn("sample.initDelaySeconds is not a whole number, not waiting.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (Boolean.parseBoolean(properties.getProperty("sample.initFails", "false").trim())) {
            logWarn("init() returns false on purpose (sample.initFails).");
            return false;
        }
        String managerName = properties.getProperty("sample.managerName");
        manager = getManager(ObjectManagerType.SAMPLEMANAGER, managerName);

        if (manager == null) {
            logWarn("initialized WITHOUT a SAMPLEMANAGER attached " +
                            "(looked for instance name '{}' - check alkaprocessor.addmanager.{}=<manager-instance-name> " +
                            "in the service's Config.properties).",
                    managerName, pluginName);
        } else {
            logInfo("initialized, bound to manager instance [{}].", manager.getManagerName());
        }
        return true;
    }

    @Override
    public boolean run() {
        if (Boolean.parseBoolean(properties.getProperty("sample.runFails", "false").trim())) {
            logWarn("run() returns false on purpose (sample.runFails).");
            return false;
        }
        logInfo("running.");
        int period = 2;
        try {
            period = Math.max(1, Integer.parseInt(properties.getProperty("sample.heartbeatSeconds", "2").trim()));
        } catch (NumberFormatException e) {
            logWarn("sample.heartbeatSeconds is not a whole number, using 2.");
        }
        mode = properties.getProperty("sample.mode", "alternate").trim().toLowerCase();
        logCalls = Boolean.parseBoolean(properties.getProperty("sample.logCalls", "true").trim());
        forward = Boolean.parseBoolean(properties.getProperty("sample.forward", "false").trim());
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::heartbeat, 1, period, TimeUnit.SECONDS);

        long millis = 500;
        try {
            millis = Math.max(0, Long.parseLong(properties.getProperty("sample.messageMillis", "500").trim()));
        } catch (NumberFormatException e) {
            logWarn("sample.messageMillis is not a whole number, using 500.");
        }
        messages = properties.getProperty("sample.messages", "...do iza svemira,|....i nazad!").split("\\|");
        messages = java.util.Arrays.stream(messages).map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (millis > 0 && messages.length > 0) scheduler.scheduleAtFixedRate(this::say, millis, millis, TimeUnit.MILLISECONDS);
        return true;
    }

    // writes the configured messages one after another; like heartbeat() it must not let an exception escape
    private void say() {
        try {
            String[] list = messages;
            logInfo("{}", list[Math.floorMod(said.getAndIncrement(), list.length)]);
        } catch (Exception e) {
            logError("message failed, will retry on the next tick.", e);
        }
    }

    // must catch everything: the JDK cancels all later scheduleAtFixedRate runs after the first exception
    private void heartbeat() {
        try {
            int n = tick.incrementAndGet();
            if (manager == null) {
                logWarn("tick #{} - still no manager attached.", n);
                return;
            }
            boolean sync = mode.equals("sync") || (!mode.equals("async") && n % 2 == 0);
            if (sync) {
                Object result = manager.processSync("sync-request-" + n);
                if (logCalls) logInfo("SYNC  -> {}", result);
                if (forward) onSyncEvent(result);
            } else {
                manager.processAsync("async-request-" + n);
                if (logCalls) logInfo("ASYNC -> dispatched request #{}", n);
                if (forward) onAsyncEvent("async-request-" + n);
            }
        } catch (Exception e) {
            logError("heartbeat failed, will retry on the next tick.", e);
        }
    }

    @Override
    public boolean stop() {
        if (scheduler != null) scheduler.shutdownNow();
        long delay = 0;
        try {
            delay = Math.max(0, Long.parseLong(properties.getProperty("sample.stopDelaySeconds", "0").trim()));
        } catch (NumberFormatException e) {
            logWarn("sample.stopDelaySeconds is not a whole number, using 0.");
        }
        if (delay > 0) {
            logInfo("stop() waits {} s (sample.stopDelaySeconds).", delay);
            try {
                Thread.sleep(delay * 1000);
            } catch (InterruptedException e) {
                logWarn("the wait in stop() was interrupted.");
                Thread.currentThread().interrupt();
            }
        }
        logInfo("stopped.");
        return true;
    }

    @Override
    public Object accept(Object o) {
        String failure = properties.getProperty("sample.acceptFailure", "none").trim().toLowerCase();
        if (failure.equals("exception")) throw new IllegalStateException("sample.acceptFailure=exception");
        if (failure.equals("error")) throw new AssertionError("sample.acceptFailure=error");
        logInfo("accepted: {}", o);
        return o;
    }
}
