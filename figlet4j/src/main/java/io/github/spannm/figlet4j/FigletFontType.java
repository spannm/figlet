package io.github.spannm.figlet4j;

import java.util.Arrays;
import java.util.Locale;

/**
 * Represents the supported font file types for figlet4j.
 *
 * @since 1.0.0
 */
public enum FigletFontType {

    /** FIGlet font format. */
    FIGFONT(".flf", "FIGlet font"),

    /** TOIlet font format. */
    TOILET_FONT(".tlf", "TOIlet font");

    private final String extension;
    private final String description;

    FigletFontType(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    /**
     * Returns the file extension including the leading dot.
     *
     * @return the file extension
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Returns a human-readable description of the font type.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    static boolean isSupported(String fileName) {
        String lcFileName = fileName.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(type -> lcFileName.endsWith(type.extension));
    }

    static String removeExtension(String fileName) {
        String lcFileName = fileName.toLowerCase(Locale.ROOT);
        for (FigletFontType type : values()) {
            if (lcFileName.endsWith(type.extension)) {
                return fileName.substring(0, fileName.length() - type.extension.length());
            }
        }
        return fileName;
    }

}
