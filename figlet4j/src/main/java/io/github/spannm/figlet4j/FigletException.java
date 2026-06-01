package io.github.spannm.figlet4j;

/**
 * Unchecked exception thrown for all error conditions in the <em>figlet4j</em> library.
 * <p>
 * Typical causes include:
 * <ul>
 *   <li>A requested font was not found on the classpath or file system</li>
 *   <li>A {@code .flf} or {@code .tlf} file could not be parsed (malformed header, truncated data)</li>
 *   <li>The input text contains a character not supported by the selected font
 *       (only when strict mode is active)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public class FigletException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FigletException(String message) {
        super(message);
    }

    public FigletException(String message, Throwable cause) {
        super(message, cause);
    }

}
