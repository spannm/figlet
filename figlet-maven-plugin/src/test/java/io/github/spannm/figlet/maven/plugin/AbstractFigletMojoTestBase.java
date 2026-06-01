package io.github.spannm.figlet.maven.plugin;

import org.apache.maven.plugin.logging.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared test infrastructure for all figlet-maven-plugin Mojo tests.
 * <p>
 * Provides:
 * <ul>
 *   <li>A {@link CapturingLog} that records every log message without
 *       printing to the console — no Mockito required.</li>
 *   <li>A reflection helper {@link #setField} to inject private
 *       {@code @Parameter}-annotated fields without modifying production code.</li>
 * </ul>
 * <p>
 * Extend this class instead of writing boilerplate in every test.
 */
@SuppressWarnings({"checkstyle:LeftCurly", "checkstyle:VisibilityModifier"})
abstract class AbstractFigletMojoTestBase {

    /**
     * Sets a private (or package-private) field anywhere in the class hierarchy
     * of {@code target}.
     * <p>
     * Use this to inject {@code @Parameter}-annotated fields that Maven
     * would normally populate at runtime.
     *
     * @param target    instance whose field should be set
     * @param fieldName simple (unqualified) field name
     * @param value     value to inject; may be {@code null}
     * @throws RuntimeException if the field does not exist or cannot be accessed
     */
    protected static void setField(Object target, String fieldName, Object value) {
        try {
            findField(target, fieldName).set(target, value);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Cannot access field '" + fieldName + "'", ex);
        }
    }

    /**
     * Reads the value of a private (or package-private) field anywhere in the class hierarchy
     * of {@code target}.
     * <p>
     * Use this helper in tests to verify the internal state of a Mojo after execution
     * without adding public getter methods to production code.
     *
     * @param target    the instance whose field should be read
     * @param fieldName the simple (unqualified) name of the field to read
     * @return the value of the field, which may be {@code null}
     * @throws RuntimeException if the field does not exist or cannot be accessed
     */
    protected static Object getField(Object target, String fieldName) {
        try {
            return findField(target, fieldName).get(target);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Cannot access field '" + fieldName + "'", ex);
        }
    }

    /**
     * Looks up a field anywhere in the class hierarchy of the given target object and
     * makes it accessible.
     * <p>
     * Walks superclasses iteratively until the field is found.
     *
     * @param target    the instance whose class hierarchy should be searched
     * @param fieldName the simple name of the field
     * @return the accessible {@link Field} instance
     * @throws RuntimeException if the field cannot be found in the entire hierarchy
     */
    private static Field findField(Object target, String fieldName) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ex) {
                cls = cls.getSuperclass(); // walk up — field may be in a superclass
            }
        }
        throw new RuntimeException(
            "Field '" + fieldName + "' not found in class hierarchy of " + target.getClass().getName());
    }

    /**
     * Installs a fresh {@link CapturingLog} into {@code mojo} via
     * {@link AbstractFigletMojo#setLog} and returns it for assertions.
     *
     * @param mojo the Mojo instance to configure
     * @return the installed in-memory log captor
     */
    protected static CapturingLog installCapturingLog(AbstractFigletMojo mojo) {
        CapturingLog log = new CapturingLog();
        mojo.setLog(log);
        return log;
    }

    /**
     * In-memory {@link Log} implementation.
     * <p>
     * Every overload funnels into a single list per level so tests never
     * have to worry about which overload the production code calls.
     */
    static final class CapturingLog implements Log {

        /** Captured debug level messages. */
        final List<String> debugMessages = new ArrayList<>();
        /** Captured info level messages. */
        final List<String> infoMessages  = new ArrayList<>();
        /** Captured warn level messages. */
        final List<String> warnMessages  = new ArrayList<>();
        /** Captured error level messages. */
        final List<String> errorMessages = new ArrayList<>();

        /** Controls the return value of {@link #isDebugEnabled()}; {@code true} by default. */
        private boolean debugEnabled = true;

        /** Overrides what {@link #isDebugEnabled()} reports, to exercise debug-gated code paths. */
        void setDebugEnabled(boolean enabled) {
            debugEnabled = enabled;
        }

        @Override public boolean isDebugEnabled()                          { return debugEnabled; }
        @Override public void debug(CharSequence msg)                      { debugMessages.add(str(msg)); }
        @Override public void debug(CharSequence msg, Throwable t)         { debugMessages.add(str(msg, t)); }
        @Override public void debug(Throwable t)                           { debugMessages.add(str(t));   }

        @Override public boolean isInfoEnabled()                           { return true; }
        @Override public void info(CharSequence msg)                       { infoMessages.add(str(msg));  }
        @Override public void info(CharSequence msg, Throwable t)          { infoMessages.add(str(msg, t)); }
        @Override public void info(Throwable t)                            { infoMessages.add(str(t));    }

        @Override public boolean isWarnEnabled()                           { return true; }
        @Override public void warn(CharSequence msg)                       { warnMessages.add(str(msg));  }
        @Override public void warn(CharSequence msg, Throwable t)          { warnMessages.add(str(msg, t)); }
        @Override public void warn(Throwable t)                            { warnMessages.add(str(t));    }

        @Override public boolean isErrorEnabled()                          { return true; }
        @Override public void error(CharSequence msg)                      { errorMessages.add(str(msg)); }
        @Override public void error(CharSequence msg, Throwable t)         { errorMessages.add(str(msg, t)); }
        @Override public void error(Throwable t)                           { errorMessages.add(str(t));   }

        /** {@code true} if any debug line contains {@code substring}. */
        boolean debugContains(String substring) {
            return debugMessages.stream().anyMatch(s -> s.contains(substring));
        }

        /** {@code true} if any info line contains {@code substring}. */
        boolean infoContains(String substring) {
            return infoMessages.stream().anyMatch(s -> s.contains(substring));
        }

        /** {@code true} if any warn line contains {@code substring}. */
        boolean warnContains(String substring) {
            return warnMessages.stream().anyMatch(s -> s.contains(substring));
        }

        /** {@code true} if any error line contains {@code substring}. */
        boolean errorContains(String substring) {
            return errorMessages.stream().anyMatch(s -> s.contains(substring));
        }

        /** All debug lines joined by {@code \n} — useful in assertion failure messages. */
        String debugAsString() {
            return String.join("\n", debugMessages);
        }

        /** All info lines joined by {@code \n} — useful in assertion failure messages. */
        String infoAsString() {
            return String.join("\n", infoMessages);
        }

        /** All warn lines joined by {@code \n} — useful in assertion failure messages. */
        String warnAsString() {
            return String.join("\n", warnMessages);
        }

        /** All error lines joined by {@code \n} — useful in assertion failure messages. */
        String errorAsString() {
            return String.join("\n", errorMessages);
        }

        /** Total number of captured messages across all levels. */
        int totalMessageCount() {
            return debugMessages.size() + infoMessages.size() + warnMessages.size() + errorMessages.size();
        }

        /** Clears all captured messages from all levels. */
        void clear() {
            debugMessages.clear();
            infoMessages.clear();
            warnMessages.clear();
            errorMessages.clear();
        }

        /**
         * Standardizes any object to its string representation.
         *
         * @param o the object to transform
         * @return string representation or {@code "<null>"}
         */
        private static String str(Object o) {
            return o == null ? "<null>" : o.toString();
        }

        /**
         * Combines a log message text and its associated throwable.
         *
         * @param msg the log message context
         * @param t the exception, may be {@code null}
         * @return the formatted combined string
         */
        private static String str(CharSequence msg, Throwable t) {
            String base = str(msg);
            return t == null ? base : base + " | " + t;
        }
    }

}
