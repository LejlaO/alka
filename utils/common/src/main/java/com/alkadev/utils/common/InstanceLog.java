package com.alkadev.utils.common;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

/**
 * Writes a log line that always starts with the instance name in brackets, like {@code [samplepluginA] connected}.
 * The Studio finds the box a line belongs to by that bracketed name: it files the line under that plugin or manager in the Logs panel
 * and counts its warnings and errors on the box. A line without the name is filed under the engine.
 * Plugins and managers use it through {@code logInfo}, {@code logWarn}, {@code logError} and {@code logDebug} of their base class.
 */
public final class InstanceLog {
    private InstanceLog() {
    }

    // message may use {} placeholders; a Throwable as the last argument is logged with its stack trace
    public static void log(Logger logger, Level level, String instance, String message, Object... args) {
        if (!logger.isEnabled(level)) return;
        logger.log(level, "[" + instance + "] " + message, args);
    }
}
