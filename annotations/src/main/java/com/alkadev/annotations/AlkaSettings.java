package com.alkadev.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// put on the Manager, Plugin or engine class: at compile time a documented, empty settings file is generated from it
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface AlkaSettings {
    String description() default "";

    /** For a manager: the type of manager it is (the name its constructor gives to {@code ObjectManagerType}, for example "SAMPLEMANAGER"). Written into the template, so the Studio can check a wiring. */
    String managerType() default "";

    /** For a plugin: the types of manager it asks for with {@code getManager(type, name)}. Written into the template; the Studio and the engine report a plugin that is wired to a manager of another type. */
    String[] usesManagerTypes() default {};

    Setting[] value();
}
