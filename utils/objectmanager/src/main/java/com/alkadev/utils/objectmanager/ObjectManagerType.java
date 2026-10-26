package com.alkadev.utils.objectmanager;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : lejla
 * @since : 6/28/22, Tue
 **/

/** Open set of manager types: new ones via {@code of("NAME")}, case-insensitive, one shared instance per name. */
public final class ObjectManagerType {
    private static final ConcurrentHashMap<String, ObjectManagerType> REGISTRY = new ConcurrentHashMap<>();

    public static final ObjectManagerType SAMPLEMANAGER = of("SAMPLEMANAGER");

    private final String name;

    private ObjectManagerType(String name) {
        this.name = name;
    }

    public static ObjectManagerType of(String name) {
        return REGISTRY.computeIfAbsent(name.toUpperCase(), ObjectManagerType::new);
    }

    @Override
    public String toString() {
        return name;
    }
}
