package ${package}.manager;

import com.alkadev.annotations.AlkaSettings;
import com.alkadev.annotations.Setting;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A manager owns something that several plugins use together: a connection, a cache, a device. Here it owns nothing but a counter and a greeting,
 * so that the example stays small. Plugins ask the engine for a manager by its type and its name and then call {@link #process(Object)}.
 * <ul>
 * <li>The engine creates the class by reflection from the {@code classname} entry of the jar, so it needs a public constructor without arguments.
 * The constructor says which type of manager this is. A type is a name: {@code ObjectManagerType.of("GREETER")} is the same object everywhere.</li>
 * <li>The life of a manager is init(), then run(), then stop(). The settings ({@code properties}) and the name of the instance ({@code managerName})
 * are there from init() on, not in the constructor. Each method returns true when it went well; false tells the engine that this instance failed.</li>
 * <li>process() is called by several threads at once, so it must be thread-safe.</li>
 * <li>One jar can be started under several names in one service (greeter, greeter2, ...). Each is a separate object with its own settings file.</li>
 * </ul>
 */
@AlkaSettings(description = "Answers every request with a greeting and counts the requests.", managerType = "GREETER", value = {
        // Every setting of the class is declared here. The build writes them into the jar as a template, and the Studio shows them as a form.
        @Setting(key = "greeter.greeting", defaultValue = "Hello", description = "The first word of every answer.")
})
public class GreeterManager extends ObjectManager {
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String greeting = "Hello";

    public GreeterManager() {
        super(ObjectManagerType.of("GREETER"));
    }

    @Override
    public boolean init() {
        greeting = properties.getProperty("greeter.greeting", "Hello");
        logInfo("initialized, greeting '{}'.", greeting);
        return true;
    }

    @Override
    public boolean run() {
        logInfo("running.");
        return true;
    }

    @Override
    public boolean stop() {
        logInfo("stopped after {} request(s).", requests.get());
        return true;
    }

    @Override
    public Object process(Object request) {
        int n = requests.incrementAndGet();
        String answer = greeting + " from " + managerName + ", request " + n + ": " + request;
        logInfo("{}", answer);
        return answer;
    }
}
