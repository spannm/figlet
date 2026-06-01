package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link FigletFontCommentNormalizer}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletFontCommentNormalizerTest {

    @Test
    void privateConstructor_shouldThrowUnsupportedOperationException() throws NoSuchMethodException {
        Constructor<?> constructor = FigletFontCommentNormalizer.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Utility class %s cannot be instantiated", FigletFontCommentNormalizer.class.getSimpleName());
    }

    @Test
    void normalizeComment_nullComment_shouldReturnEmptyList() {
        assertThat(FigletFontCommentNormalizer.normalizeComment("font", null)).isEmpty();
    }

    @Test
    void normalizeComment_emptyComment_shouldReturnEmptyList() {
        assertThat(FigletFontCommentNormalizer.normalizeComment("font", List.of())).isEmpty();
    }

    @Test
    void normalizeComment_onlyBlankLines_shouldFallBackToOriginal() {
        List<String> comment = new ArrayList<>(List.of("   ", "", "\t"));
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        // Result is "swallowed" entirely -> fallback to original list
        assertThat(result).isEqualTo(comment);
    }

    @Test
    void normalizeComment_nullLine_shouldBeTreatedAsBlank() {
        List<String> comment = new ArrayList<>();
        comment.add(null);
        comment.add("Some actual text here");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Some actual text here");
    }

    @Test
    void normalizeComment_leadingBlankLines_shouldBeSkipped() {
        List<String> comment = List.of("", "   ", "Actual comment content here");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Actual comment content here");
    }

    @Test
    void normalizeComment_leadingFontFileNameLine_shouldBeSkipped() {
        // line matching "<fontname>.flf" should be dropped as a leading-junk line
        List<String> comment = List.of("standard.flf", "Real comment line follows");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("standard", comment);
        assertThat(result).containsExactly("Real comment line follows");
    }

    @Test
    void normalizeComment_leadingFontFileNameLine_caseInsensitive_shouldBeSkipped() {
        List<String> comment = List.of("STANDARD.FLF", "Real comment line follows");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("standard", comment);
        assertThat(result).containsExactly("Real comment line follows");
    }

    @Test
    void normalizeComment_leadingTlfFileNameLine_shouldBeSkipped() {
        List<String> comment = List.of("mono9.tlf", "Real comment line follows");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("mono9", comment);
        assertThat(result).containsExactly("Real comment line follows");
    }

    @Test
    void normalizeComment_tabCharacters_shouldBeReplacedWithSpaces() {
        List<String> comment = List.of("Line\twith\ttabs and enough chars");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Line    with    tabs and enough chars");
    }

    @Test
    void normalizeComment_internalBlankLines_shouldBeFilteredOut() {
        List<String> comment = List.of("First valid line here", "", "Second valid line here");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("First valid line here", "Second valid line here");
    }

    @Test
    void normalizeComment_asciiArtDivider_shouldStopProcessing() {
        // a line with no run of 3+ alphanumeric characters (ASCII art divider) terminates processing
        List<String> comment = List.of(
            "Valid comment line here",
            "--====-- @@ --====--",
            "This line should never appear");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Valid comment line here");
    }

    @Test
    void normalizeComment_explanationTerminator_shouldStopProcessing() {
        List<String> comment = List.of(
            "Valid comment line here",
            "Explanation of first line of comment",
            "This line should never appear");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Valid comment line here");
    }

    @Test
    void normalizeComment_permissionTerminator_shouldStopProcessing() {
        List<String> comment = List.of(
            "Valid comment line here",
            "Permission is hereby given to modify this font",
            "This line should never appear");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Valid comment line here");
    }

    @Test
    void normalizeComment_dateLine_shouldBeNormalized() {
        List<String> comment = List.of("Date: 13 Feb 1994");
        List<String> result = FigletFontCommentNormalizer.normalizeComment("font", comment);
        assertThat(result).containsExactly("Date: 1994-02-13");
    }

    @Test
    void normalizeComment_fullRealisticExample_shouldProduceCleanedResult() {
        List<String> comment = new ArrayList<>(List.of(
            "",
            "standard.flf",
            "",
            "Standard by Glenn Chappell & Ian Chai 3/93",
            "Last Change: 4 Sep 1994",
            "--==--==--==--",
            "never seen"));
        List<String> result = FigletFontCommentNormalizer.normalizeComment("standard", comment);
        assertThat(result).containsExactly(
            "Standard by Glenn Chappell & Ian Chai 3/93",
            "Date: 1994-09-04");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Date: 13 Feb 1994            | Date: 1994-02-13",
        "Date: 8 June 1994            | Date: 1994-06-08",
        "Last change: 17th June, 1994 | Date: 1994-06-17",
        "1994 Apr 2                   | Date: 1994-04-02",
        "August 11, 1994              | Date: 1994-08-11",
        "August 9, 1994               | Date: 1994-08-09",
        "Oct 23, 1994                 | Date: 1994-10-23",
        "10/11/94                     | Date: 1994-11-10",
        "1994-08-21                   | Date: 1994-08-21",
        "15 Jul 1994 00:04:25 GMT     | Date: 1994-07-15"
    })
    void normalizeDateLine_recognizedDateFormats_shouldNormalizeToIso(String input, String expected) {
        assertThat(FigletFontCommentNormalizer.normalizeDateLine(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Just a plain comment with no digits",
        "Version 1",
        "Author: John Doe, est. ~94",
        "Not a date 99/99/9999/whatever"
    })
    void normalizeDateLine_nonDateLines_shouldReturnUnchanged(String input) {
        assertThat(FigletFontCommentNormalizer.normalizeDateLine(input)).isEqualTo(input);
    }

    @Test
    void normalizeDateLine_lineWithSingleDigit_shouldReturnUnchanged() {
        // requires at least 2 consecutive digits to be considered a date candidate
        String input = "Version 5 of this font";
        assertThat(FigletFontCommentNormalizer.normalizeDateLine(input)).isEqualTo(input);
    }

    @Test
    void normalizeDateLine_unparseableDateLikeLine_shouldReturnOriginal() {
        // Contains 2+ digits but is not a recognized date format
        String input = "Build 42 release notes";
        assertThat(FigletFontCommentNormalizer.normalizeDateLine(input)).isEqualTo(input);
    }

}
