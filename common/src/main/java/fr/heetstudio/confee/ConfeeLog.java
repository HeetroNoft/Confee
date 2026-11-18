package fr.heetstudio.confee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging helper that always prefixes messages with {@code [Confee]} (spec 14).
 * <p>Internal: may change without notice.</p>
 */
public final class ConfeeLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("Confee");
    private static final String PREFIX = "[Confee] ";

    private ConfeeLog() {
    }

    public static void info(String message, Object... args) {
        LOGGER.info(PREFIX + message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(PREFIX + message, args);
    }

    public static void error(String message, Throwable error) {
        LOGGER.error(PREFIX + message, error);
    }
}
