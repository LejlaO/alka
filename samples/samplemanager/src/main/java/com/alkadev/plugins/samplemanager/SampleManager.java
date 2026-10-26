package com.alkadev.plugins.samplemanager;

import com.alkadev.annotations.AlkaSettings;
import com.alkadev.annotations.Setting;
import com.alkadev.utils.objectmanager.ObjectManager;
import com.alkadev.utils.objectmanager.ObjectManagerType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sample of a Manager: owns a resource (here an in-memory map) and exposes it through process().
 * <ul>
 * <li>A public no-arg constructor is required: the engine creates the class by reflection from the jar's {@code classname} manifest entry.</li>
 * <li>Lifecycle init() -> run() -> stop(); {@code properties} and {@code managerName} are set before init(), never in the constructor.</li>
 * <li>process() runs on several pool threads at once plus sync callers: keep it thread-safe.</li>
 * <li>One jar can be loaded under several instance names, each a separate object with its own state and config.</li>
 * </ul>
 */
@AlkaSettings(description = "Reference manager: owns an in-memory map and answers process() calls.", managerType = "SAMPLEMANAGER", value = {
        @Setting(key = "sample.seedValue", defaultValue = "default-seed", description = "Value stored under the key seed; every response mentions it.")
})
public class SampleManager extends ObjectManager {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    public SampleManager() {
        super(ObjectManagerType.SAMPLEMANAGER);
    }

    @Override
    public boolean init() {
        String seedValue = properties.getProperty("sample.seedValue", "default-seed");
        store.put("seed", seedValue);

        logInfo("initialized. seed='{}'", seedValue);
        return true;
    }

    @Override
    public boolean run() {
        logInfo("running.");
        return true;
    }

    @Override
    public boolean stop() {
        logInfo("stopped after {} request(s).", requestCount.get());
        return true;
    }

    @Override
    public Object process(Object o) {
        int n = requestCount.incrementAndGet();
        String result = "[" + managerName + "] response #" + n + " (seed=" + store.get("seed") + ") to: " + o;
        logInfo("{}", result);
        return result;
    }
}
