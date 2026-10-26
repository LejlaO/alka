package ${package}.plugin;

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
 * A plugin does the work of a service. This one says hello to a manager at a fixed interval and logs the answer. A real plugin would read a queue,
 * answer requests of other plugins, call a web service, and so on.
 * <ul>
 * <li>Like a manager, the engine creates it by reflection: a public constructor without arguments, and {@code properties} and {@code pluginName} are there from
 * init() on.</li>
 * <li>A plugin does not know which manager it will get. In init() it asks for a manager by type and by the name that its settings give
 * ({@code hello.managerName}); the service decides in its config file which managers a plugin may use ({@code alkaprocessor.addmanager.hello=greeter}).</li>
 * <li>A plugin can also be sent requests by other plugins: that is accept(). It is called by several threads at once.</li>
 * <li>Threads that a plugin starts must be stopped in stop().</li>
 * </ul>
 */
@AlkaSettings(description = "Sends a greeting to its manager every few seconds and logs the answer.", usesManagerTypes = "GREETER", value = {
        @Setting(key = "hello.managerName", description = "The name of the manager instance to greet (it must be in alkaprocessor.addmanager of the service)."),
        @Setting(key = "hello.intervalSeconds", type = SettingType.NUMBER, defaultValue = "2", min = "1", max = "3600",
                description = "Seconds between two greetings.")
})
public class HelloPlugin extends ObjectPlugin {
    private final AtomicInteger sent = new AtomicInteger();
    private ScheduledExecutorService scheduler;
    private volatile ObjectManager manager;

    @Override
    public boolean init() {
        // A manager has a type, and the type is a name. The plugin does not need the class of the manager, only the name of its type.
        manager = getManager(ObjectManagerType.of("GREETER"), properties.getProperty("hello.managerName"));
        if (manager == null) {
            logWarn("no manager: check hello.managerName and alkaprocessor.addmanager.{} in the config of the service.", pluginName);
        } else {
            logInfo("initialized, bound to manager [{}].", manager.getManagerName());
        }
        return true;
    }

    @Override
    public boolean run() {
        int seconds = 2;
        try {
            seconds = Math.max(1, Integer.parseInt(properties.getProperty("hello.intervalSeconds", "2").trim()));
        } catch (NumberFormatException e) {
            logWarn("hello.intervalSeconds is not a whole number, using 2.");
        }
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::greet, 1, seconds, TimeUnit.SECONDS);
        logInfo("running, greeting every {} s.", seconds);
        return true;
    }

    // An exception that escapes ends a scheduled task for good, so everything is caught here.
    private void greet() {
        try {
            if (manager == null) return;
            Object answer = manager.processSync("hello " + sent.incrementAndGet());
            logInfo("the manager answered: {}", answer);
        } catch (Exception e) {
            logError("greeting failed, will try again.", e);
        }
    }

    @Override
    public boolean stop() {
        if (scheduler != null) scheduler.shutdownNow();
        logInfo("stopped.");
        return true;
    }

    @Override
    public Object accept(Object request) {
        logInfo("accepted: {}", request);
        return request;
    }
}
