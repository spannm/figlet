package io.github.spannm.figlet4j;

import static java.util.stream.Collectors.toCollection;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Component for cleaning up and parsing FIGLET font comment sections.
 * <p>
 * It filters out metadata headers, skips leading blank lines, normalizes various
 * date string formats into standard ISO-8601 dates, and terminates processing
 * as soon as raw FIGLET character definitions (ASCII art dividers) begin.
 *
 * @since 1.0.0
 */
final class FigletFontCommentNormalizer {

    private static final Logger            LOGGER                    = System.getLogger(FigletFontCommentNormalizer.class.getName());

    // matches explicit prefixes like "Date:" or "Last change:" case-insensitive at the start
    private static final Pattern           KNOWN_PREFIX_PATTERN      = Pattern.compile(
        "(?i)^(?:date:\\s*|last\\s+change:\\s*)");

    // removes English ordinal suffixes from days (e.g., 17th -> 17, 23rd -> 23)
    private static final Pattern           ORDINAL_SUFFIX_PATTERN    = Pattern.compile(
        "(?i)\\b(\\d+)(?:st|nd|rd|th)\\b");

    // truncates timestamps and timezones from the end (e.g., 00:04:25 GMT)
    private static final Pattern           TIMESTAMP_CLEANUP_PATTERN = Pattern.compile(
        "\\s+\\d{2}:\\d{2}:\\d{2}\\s+GMT$");

    // checks if a line contains at least three or more alphanumeric characters
    private static final Pattern           ALPHANUMERIC_PATTERN      = Pattern.compile(
        "[a-zA-Z0-9]{3,}");

    // checks if a line contains at least two digits
    private static final Pattern           HAS_DIGITS_PATTERN        = Pattern.compile(
        "[0-9]{2,}");

    private static final DateTimeFormatter FLEXIBLE_DATE_FORMATTER   = new DateTimeFormatterBuilder()
        .appendOptional(DateTimeFormatter.ofPattern("d MMM yyyy"))   // 13 Feb 1994 / 8 June 1994
        .appendOptional(DateTimeFormatter.ofPattern("d MMMM, yyyy")) // 17 June, 1994 (after ordinal strip)
        .appendOptional(DateTimeFormatter.ofPattern("d MMMM yyyy"))  // 8 June 1994
        .appendOptional(DateTimeFormatter.ofPattern("yyyy MMM d"))   // 1994 Apr 2
        .appendOptional(DateTimeFormatter.ofPattern("MMMM d, yyyy")) // August 11, 1994 / August 9, 1994
        .appendOptional(DateTimeFormatter.ofPattern("MMM d, yyyy"))  // Oct 23, 1994
        .appendOptional(new DateTimeFormatterBuilder()               // 10/11/94
            .appendPattern("d/M/")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1900)
            .toFormatter(Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd"))   // Fallback ISO
        .toFormatter(Locale.ENGLISH);

    private FigletFontCommentNormalizer() {
        throw new UnsupportedOperationException(
            "Utility class " + getClass().getSimpleName() + " cannot be instantiated");
    }

    static List<String> normalizeComment(String fontName, List<String> comment) {
        if (comment == null || comment.isEmpty()) {
            return new ArrayList<>();
        }

        String lcFontName = fontName.toLowerCase(Locale.ROOT);

        List<String> result = comment.stream()
            // sanitize early so drop/take logic operates on clean strings
            .map(line -> line == null ? "" : line.replace("\t", "    ").strip())

            // skip initial junk (leading blanks or rows matching the font file name)
            .dropWhile(line -> line.isBlank()
                       || isFileNameMatch(line, lcFontName))

            // stop comment processing if line looks like ASCII art
            .takeWhile(line -> line.isBlank()
                       || (ALPHANUMERIC_PATTERN.matcher(line).find()
                           && !line.startsWith("Explanation of first line")
                           && !line.startsWith("Permission is hereby given")))

            // filter internal blank lines from the final output list
            .filter(line -> !line.isBlank())

            // transform dates if applicable
            .map(FigletFontCommentNormalizer::normalizeDateLine)
            .collect(toCollection(ArrayList::new));

        if (result.isEmpty()) {
            // fallback if processing has 'swallowed' all lines
            return comment;
        }
        return result;
    }

    private static boolean isFileNameMatch(String line, String fontName) {
        String lcLine = line.toLowerCase(Locale.ROOT);
        for (FigletFontType type : FigletFontType.values()) {
            if ((fontName + type.getExtension()).equals(lcLine)) {
                return true;
            }
        }
        return false;
    }

    static String normalizeDateLine(String line) {
        if (!HAS_DIGITS_PATTERN.matcher(line).find()) { // not a date
            return line;
        }

        // clean the line from known prefixes to isolate the raw date string
        String rawDate = KNOWN_PREFIX_PATTERN.matcher(line).replaceFirst("").strip();

        // clean up ordinals: "17th June, 1994" -> "17 June, 1994"
        rawDate = ORDINAL_SUFFIX_PATTERN.matcher(rawDate).replaceAll("$1");

        // clean up timestamps: "15 Jul 1994 00:04:25 GMT" -> "15 Jul 1994"
        rawDate = TIMESTAMP_CLEANUP_PATTERN.matcher(rawDate).replaceAll("");

        try {
            // try to parse with our flexible formatter
            LocalDate parsedDate = LocalDate.parse(rawDate, FLEXIBLE_DATE_FORMATTER);
            return "Date: " + parsedDate;
        } catch (DateTimeParseException ex) {
            // if not a valid date format, return original untouched line
            LOGGER.log(Level.DEBUG, "Could not parse date in line: {0}", line);
            return line;
        }
    }

}

