package com.alkadev.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({})
public @interface Setting {
    String key();

    String description() default "";

    SettingType type() default SettingType.STRING;

    String defaultValue() default "";

    // for LIST: the allowed values
    String[] options() default {};

    // for NUMBER: optional bounds, written as numbers in a string
    String min() default "";

    String max() default "";

    // a setting the component cannot work without: it has no usable default and the Studio flags it while it is missing
    boolean required() default false;
}
