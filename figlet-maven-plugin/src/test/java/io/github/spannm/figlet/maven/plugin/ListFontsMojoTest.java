package io.github.spannm.figlet.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.spannm.figlet4j.FigletFontRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Unit tests for {@link ListFontsMojo}.
 * <p>
 * All tests run the mojo directly (no Maven container) and capture log
 * output via the {@link CapturingLog} from the base class.
 * <p>
 * The {@code randomSampleText} tests exercise the package-private
 * {@link ListFontsMojo#randomSampleText(Clock, Locale)} overload with a
 * {@link Clock#fixed} clock and a fixed {@link Locale}. This pins the
 * weekday, month, language, country, year and greeting candidates to known
 * values so the random choice among them can be asserted deterministically,
 * instead of relying on whatever the system clock/locale happen to be.
 */
@SuppressWarnings("checkstyle:MethodName")
final class ListFontsMojoTest extends AbstractFigletMojoTestBase {

    /** Friday, 15 March 2024, 09:30 UTC — an arbitrary but fixed weekday/month/hour combination. */
    private static final Clock FRIDAY_MORNING = Clock.fixed(Instant.parse("2024-03-15T09:30:00Z"), ZoneOffset.UTC);

    private ListFontsMojo mojo;
    private CapturingLog  log;

    @BeforeEach
    void setUp() {
        mojo = new ListFontsMojo();
        log  = installCapturingLog(mojo);
        // sensible default width so rendering does not wrap unexpectedly
        setField(mojo, "parmWidth", 200);
    }

    @Test
    void defaultRun_printsHeader() throws Exception {
        mojo.execute();

        assertThat(log.infoAsString()).contains("Available fonts");
    }

    @Test
    void defaultRun_printsFontNames() throws Exception {
        mojo.execute();

        // "standard" and "banner" ship with figlet4j — at least one must appear
        assertThat(log.infoMessages)
            .anyMatch(s -> s.contains("standard") || s.contains("banner"));
    }

    @Test
    void defaultRun_printsHints() throws Exception {
        mojo.execute();

        assertThat(log.infoAsString()).contains("figlet.metadata=true", "figlet.sample=true");
    }

    @Test
    void metadataTrue_noSeparatorLines() throws Exception {
        setField(mojo, "parmMetadata", true);
        mojo.execute();

        assertThat(log.infoAsString()).doesNotContain("·");
    }

    @Test
    void metadataTrue_includesCommentContent() throws Exception {
        setField(mojo, "parmMetadata", true);
        mojo.execute();

        // FIGlet font comment blocks typically contain the font name or "FigFont"
        long linesWithContent = log.infoMessages.stream()
            .filter(s -> s.trim().length() > 2)
            .count();
        assertThat(linesWithContent).isGreaterThan(5);
    }

    @Test
    void sampleTrue_noSampleText_rendersNonTrivialOutput() throws Exception {
        setField(mojo, "parmPrintSample", true);
        // sampleText intentionally left null
        mojo.execute();

        long nonTrivialLines = log.infoMessages.stream()
            .filter(s -> s.trim().length() > 5)
            .count();
        assertThat(nonTrivialLines).isGreaterThan(10);
    }

    @Test
    void sampleTrue_blankSampleText_fallsBackToRandomText() throws Exception {
        setField(mojo, "parmPrintSample", true);
        setField(mojo, "parmSampleText",  "   ");  // blank — must fall back to a random default
        mojo.execute();

        long nonTrivialLines = log.infoMessages.stream()
            .filter(s -> s.trim().length() > 5)
            .count();
        assertThat(nonTrivialLines).isGreaterThan(10);
    }

    @Test
    void sampleTrue_customSampleText_rendersText() throws Exception {
        setField(mojo, "parmPrintSample", true);
        setField(mojo, "parmSampleText",  "Hi");
        mojo.execute();

        long nonTrivialLines = log.infoMessages.stream()
            .filter(s -> s.trim().length() > 5)
            .count();
        assertThat(nonTrivialLines).isGreaterThan(10);
    }

    @Test
    void metadataAndSample_fontNameAppearsExactlyOnce() throws Exception {
        setField(mojo, "parmMetadata",    true);
        setField(mojo, "parmPrintSample", true);
        setField(mojo, "parmSampleText",  "X");
        mojo.execute();

        // count lines that start with "  standard" (two leading spaces = font name prefix)
        long occurrences = log.infoMessages.stream()
            .filter(s -> s.contains(" standard "))
            .count();

        assertThat(occurrences).as("Font name 'standard' must appear exactly once").isEqualTo(1);
    }

    @Test
    void metadataTrue_fontWithNoComments_printsNameOnlyWithoutFailure(@TempDir Path tmp) throws Exception {
        Path flfFile = tmp.resolve("no-comment.flf");
        Files.writeString(flfFile, buildMinimalFlf(1, 0));
        String uniqueName = "no-comment-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, flfFile);

        setField(mojo, "parmMetadata", true);
        mojo.execute();

        assertThat(log.infoAsString()).contains(uniqueName);
        assertThat(log.warnMessages).noneMatch(s -> s.contains(uniqueName));
    }

    @Test
    void metadataAndSampleTrue_unloadableExternalFont_reportsFailureAndWarnsSummary(@TempDir Path tmp)
            throws Exception {
        Path missing = tmp.resolve("does-not-exist.flf");
        String uniqueName = "broken-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, missing);

        setField(mojo, "parmMetadata", true);
        setField(mojo, "parmPrintSample", true);
        mojo.execute();

        assertThat(log.warnMessages)
            .as("Expected a warning while loading metadata for the unloadable font.\n" + log.warnAsString())
            .anyMatch(s -> s.contains(uniqueName) && s.contains("Could not load metadata"));
        assertThat(log.warnAsString())
            .as("Expected the trailing failure-count summary warning")
            .contains("could not be fully processed");
    }

    @Test
    void skip_producesNoFontOutput() throws Exception {
        setField(mojo, "parmSkip", true);
        mojo.execute();

        assertThat(log.infoAsString()).doesNotContain("Available FIGfonts");
        assertThat(log.infoAsString()).contains("figlet.skip=true");
    }

    /** The public no-arg entry point (real system clock/locale) must never produce garbage output. */
    @Test
    void randomSampleText_realClockAndLocale_alwaysAsciiPrintable() {
        for (int i = 0; i < 200; i++) {
            String text = ListFontsMojo.randomSampleText();
            assertThat(text).isNotBlank();
            assertThat(text).as("must stay within the ASCII range every FIGfont supports")
                .matches("[\\x20-\\x7E]+");
        }
    }

    /** With a fixed clock/locale, every draw must be one of the six known candidates — nothing else. */
    @Test
    void randomSampleText_fixedClockAndLocale_onlyReturnsKnownCandidates() {
        Set<String> expected = Set.of("Friday", "March", "German", "Germany", "2024", "Morning");

        for (int i = 0; i < 200; i++) {
            String text = ListFontsMojo.randomSampleText(FRIDAY_MORNING, Locale.GERMANY);
            assertThat(expected).as("returned value must be one of the six known candidates").contains(text);
        }
    }

    /** Over enough draws, the random pick must eventually surface all six candidates, not just a subset. */
    @Test
    void randomSampleText_fixedClockAndLocale_eventuallyCoversAllCandidates() {
        Set<String> expected = Set.of("Friday", "March", "German", "Germany", "2024", "Morning");

        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(ListFontsMojo.randomSampleText(FRIDAY_MORNING, Locale.GERMANY));
        }

        assertThat(seen).as("every one of the six candidates should surface given enough draws")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    /** A locale without a country (e.g. {@link Locale#ENGLISH}) must have its blank country candidate filtered out. */
    @Test
    void randomSampleText_localeWithoutCountry_neverReturnsBlankCandidate() {
        for (int i = 0; i < 200; i++) {
            String text = ListFontsMojo.randomSampleText(FRIDAY_MORNING, Locale.ENGLISH);
            assertThat(text).isNotBlank();
            assertThat(text).isNotEqualTo(Locale.ENGLISH.getDisplayCountry(Locale.ENGLISH));
        }
    }

    /** The time-of-day greeting candidate must match the documented hour boundaries (6/12/18/22). */
    @ParameterizedTest
    @CsvSource({
        "0,  Night",
        "5,  Night",
        "6,  Morning",
        "11, Morning",
        "12, Afternoon",
        "17, Afternoon",
        "18, Evening",
        "21, Evening",
        "22, Night",
        "23, Night"
    })
    void randomSampleText_timeOfDayGreeting_matchesHourBoundary(int hour, String expectedGreeting) {
        Clock clock = Clock.fixed(Instant.parse(String.format("2024-03-15T%02d:00:00Z", hour)), ZoneOffset.UTC);

        boolean greetingSeen = IntStream.range(0, 300)
            .mapToObj(i -> ListFontsMojo.randomSampleText(clock, Locale.GERMANY))
            .anyMatch(expectedGreeting::equals);

        assertThat(greetingSeen)
            .as("hour %d should eventually surface \"%s\" among enough draws", hour, expectedGreeting)
            .isTrue();
    }

    /**
     * Builds a minimal valid FLF file with no comment lines and the required
     * 102 glyphs (ASCII 32-126 + 7 Deutsch), each a single space row.
     */
    private static String buildMinimalFlf(int height, int commentLines) {
        StringBuilder sb = new StringBuilder();
        sb.append("flf2a$ ").append(height).append(" ").append(height)
          .append(" 2 0 ").append(commentLines).append('\n');
        for (int i = 0; i < commentLines; i++) {
            sb.append("comment\n");
        }
        for (int i = 0; i < 102; i++) {
            sb.append(" @\n".repeat(Math.max(0, height - 1)))
              .append(" @@\n");
        }
        return sb.toString();
    }

}
