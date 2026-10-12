package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FigletRenderer}.
 * <p>
 * All tests use an in-memory test font so they are independent of bundled
 * {@code .flf} resources. The test font has height=2 and defines glyphs for
 * ASCII 32–126 plus the seven mandatory Deutsch code points.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletRendererTest {

    // test font setup

    /**
     * Minimal test glyph: 2 rows, 4 columns.
     * Row 0: "##  "  (2 visible, 2 trailing spaces)
     * Row 1: " ##  " stripped to "##  " after leading-space trim — but for
     *        a uniform glyph we just use the same pattern for all characters.
     */
    private static final char[][] GLYPH_NORMAL = {
        {'#', '#', ' ', ' '},
        {'#', '#', ' ', ' '}
    };

    /** Glyph with 1 leading and 1 trailing space on every row. */
    private static final char[][] GLYPH_PADDED = {
        {' ', '#', '#', ' '},
        {' ', '#', '#', ' '}
    };

    /** Glyph whose rows are entirely spaces (used for the space character). */
    private static final char[][] GLYPH_SPACE = {
        {' ', ' ', ' ', ' '},
        {' ', ' ', ' ', ' '}
    };

    /** Space glyph made of a blank and a hardblank, as most real fonts define it. */
    private static final char[][] GLYPH_HARDBLANK_SPACE = {
        {' ', '$'},
        {' ', '$'}
    };

    /**
     * Asymmetric glyph 'A': wide on top, narrower visible content on bottom.
     * Row 0: "##  " — trailing=2, leading=0
     * Row 1: "#   " — trailing=3, leading=0
     */
    private static final char[][] GLYPH_A = {
        {'#', '#', ' ', ' '},
        {'#', ' ', ' ', ' '}
    };

    /**
     * Asymmetric glyph 'B': leading spaces vary per row.
     * Row 0: "  ##" — trailing=0, leading=2
     * Row 1: " ## " — trailing=1, leading=1
     */
    private static final char[][] GLYPH_B = {
        {' ', ' ', '#', '#'},
        {' ', '#', '#', ' '}
    };

    private FigletFont testFont;
    private FigletFont asymmetricFont;

    @BeforeEach
    void setUp() {
        testFont = buildUniformFont(GLYPH_NORMAL, GLYPH_SPACE);
        asymmetricFont = buildAsymmetricFont();
    }

    @Test
    void privateConstructor_shouldThrowException() {
        // FigletRenderer is not a utility class — just verify NPE on null font
        assertThatThrownBy(() -> new FigletRenderer(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("font");
    }

    @Test
    void withWidth_defaultWidth_shouldBe72() {
        FigletRenderer renderer = new FigletRenderer(testFont);
        assertThat(renderer.getWidth()).isEqualTo(FigletRenderer.DEFAULT_WIDTH);
    }

    @Test
    void withWidth_validValue_shouldUpdateWidth() {
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(100);
        assertThat(renderer.getWidth()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100})
    void withWidth_nonPositiveValue_shouldThrowException(int invalidWidth) {
        FigletRenderer renderer = new FigletRenderer(testFont);
        assertThatThrownBy(() -> renderer.withWidth(invalidWidth))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("width must be >= 1");
    }

    @Test
    void setStrict_defaultValue_shouldBeTrue() {
        FigletRenderer renderer = new FigletRenderer(testFont);
        assertThat(renderer.isStrict()).isTrue();
    }

    @Test
    void setStrict_false_shouldDisableStrictMode() {
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(false);
        assertThat(renderer.isStrict()).isFalse();
    }

    @Test
    void withWidth_shouldSupportFluentChaining() {
        FigletRenderer renderer = new FigletRenderer(testFont)
            .withWidth(80)
            .setStrict(false);
        assertThat(renderer.getWidth()).isEqualTo(80);
        assertThat(renderer.isStrict()).isFalse();
    }

    @Test
    void render_nullText_shouldThrowNullPointerException() {
        FigletRenderer renderer = new FigletRenderer(testFont);
        assertThatThrownBy(() -> renderer.render(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("text");
    }

    @Test
    void render_emptyText_shouldReturnEmptyString() {
        String result = new FigletRenderer(testFont).render("");
        assertThat(result).isEmpty();
    }

    @Test
    void render_singleCharacter_shouldProduceHeightRows() {
        String result = new FigletRenderer(testFont).render("A");
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(testFont.getHeight());
    }

    @Test
    void render_multipleCharacters_shouldProduceHeightRows() {
        String result = new FigletRenderer(testFont).render("AB");
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(testFont.getHeight());
    }

    @Test
    void render_outputRowsShouldHaveNoTrailingSpaces() {
        String result = new FigletRenderer(testFont).render("ABC");
        for (String line : result.split("\n", -1)) {
            if (!line.isEmpty()) {
                assertThat(line).doesNotEndWith(" ");
            }
        }
    }

    // every input line, blank or not, becomes one band of height rows; a CSV value cannot hold a
    // line break, so the input uses a literal backslash-n that the test turns into a real one
    @ParameterizedTest(name = "[{index}] {2}")
    @CsvSource(delimiter = '|', value = {
        "A\\nB    | 2 | two text lines",
        "\\n      | 2 | single newline, two blank lines",
        "\\n\\n    | 3 | two newlines, three blank lines",
        "A\\n\\nB  | 3 | blank line between two text lines",
    })
    void render_newlines_producesOneBandPerLine(String input, int bands, String reason) {
        String result = new FigletRenderer(testFont).render(input.replace("\\n", "\n"));
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo((long) bands * testFont.getHeight());
    }

    @Test
    void render_strictMode_unsupportedCharacter_shouldThrowFigletException() {
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(true);
        // Emoji is not in the test font
        assertThatThrownBy(() -> renderer.render("A\uD83D\uDE00"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("not supported by font")
            .hasMessageContaining("U+");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "\uD83D\uDE00    | Character '\uD83D\uDE00' (U+1F600)",
        "\u00E9          | Character '\u00E9' (U+00E9)",
        "\"\u0001\"      | Character '\\u0001' (U+0001)"
    })
    void render_strictModeUnsupportedCharacter_messageShowsCharacter(String input, String expected) {
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(true);
        assertThatThrownBy(() -> renderer.render(input))
            .isInstanceOf(FigletException.class)
            .hasMessageStartingWith(expected);
    }

    @Test
    void render_strictMode_unsupportedCharacter_errorShouldIncludeFontName() {
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(true);
        assertThatThrownBy(() -> renderer.render("\uD83D\uDE00"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining(testFont.getName());
    }

    @Test
    void render_lenientMode_unsupportedCharacter_shouldNotThrow() {
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(false);
        // Must not throw; unsupported char maps to '?'
        assertThat(renderer.render("A\uD83D\uDE00B")).isNotEmpty();
    }

    @Test
    void render_strictMode_newlineIsNotConsideredUnsupported() {
        // \n is structural, not a glyph — should never cause a strict-mode failure
        FigletRenderer renderer = new FigletRenderer(testFont).setStrict(true);
        assertThat(renderer.render("A\nB")).isNotEmpty();
    }

    @Test
    void render_wordWrapping_longWordShouldNotBeSplit() {
        // width = 1 column — forces wrapping, but single words are never split mid-glyph
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(1);
        String result = renderer.render("AB");
        // Both chars appear in output — just on separate bands
        assertThat(result).isNotEmpty();
        long newlines = result.chars().filter(c -> c == '\n').count();
        // AB fits on one band even below width limit (single word, never split)
        assertThat(newlines).isGreaterThanOrEqualTo(testFont.getHeight());
    }

    @Test
    void render_wordWrapping_spaceTokenBetweenWordsBeyondWidth_shouldWrap() {
        // width intentionally small to force wrapping between words: "A B" is 6 columns wide
        FigletRenderer renderer = new FigletRenderer(buildUniformFont(GLYPH_NORMAL, GLYPH_HARDBLANK_SPACE)).withWidth(5);
        String result = renderer.render("A B");
        // Two words → at least 2 bands
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isGreaterThanOrEqualTo(2L * testFont.getHeight());
    }

    @Test
    void render_wordWrapping_shouldNeverExceedConfiguredWidth() {
        // Regression test: the wrap-width check must measure the real fitted
        // width, not sum a per-glyph estimate, which cannot represent the
        // fitting overlap between glyphs. "A B" is 6 columns wide here.
        FigletRenderer renderer = new FigletRenderer(buildUniformFont(GLYPH_NORMAL, GLYPH_HARDBLANK_SPACE)).withWidth(5);
        String result = renderer.render("A B");
        for (String line : result.split("\n", -1)) {
            assertThat(line.length())
                .as("line '%s' must not exceed the configured width of 5", line)
                .isLessThanOrEqualTo(5);
        }
    }

    @Test
    void render_wordWrapping_leadingSpaceOnWrappedLine_shouldBeDiscarded() {
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(5);
        String result = renderer.render("A B");
        // No output line may start with a space (leading space from wrap is discarded)
        for (String line : result.split("\n", -1)) {
            assertThat(line).doesNotStartWith(" ");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "''     | 0",
        "'#'    | 0",
        "' '    | 1",
        "' #'   | 1",
        "'  #'  | 2",
        "'  # ' | 2",
        "'   '  | 3"
    })
    void leadingSpaces_variousInputs_shouldCountCorrectly(String input, int expected) {
        assertThat(FigletRenderer.leadingSpaces(input.toCharArray())).isEqualTo(expected);
    }

    @Test
    void glyphWidth_nullGlyph_shouldReturnZero() {
        assertThat(FigletRenderer.glyphWidth(null)).isZero();
    }

    @Test
    void glyphWidth_emptyGlyph_shouldReturnZero() {
        assertThat(FigletRenderer.glyphWidth(new char[0][])).isZero();
    }

    @Test
    void glyphWidth_normalGlyph_shouldReturnFirstRowLength() {
        assertThat(FigletRenderer.glyphWidth(GLYPH_NORMAL)).isEqualTo(4);
    }

    @Test
    void render_firstGlyphWithLeadingSpaces_outputShouldNotStartWithSpace() {
        // GLYPH_PADDED has 1 leading space — it must be stripped in output
        FigletFont paddedFont = buildUniformFont(GLYPH_PADDED, GLYPH_SPACE);
        String result = new FigletRenderer(paddedFont).render("A");
        for (String line : result.split("\n", -1)) {
            if (!line.isEmpty()) {
                assertThat(line).doesNotStartWith(" ");
            }
        }
    }

    @Test
    void render_asymmetricGlyphs_fittingOverlapShouldBeConstrainedByNarrowestRow() {
        // GLYPH_A: row0 trailing=2, row1 trailing=3
        // GLYPH_B: row0 leading=2,  row1 leading=1
        // overlap per row: row0=2+2-1=3, row1=3+1-1=3 → min=3
        // Rendered width of "AB":
        //   glyphWidth(A)=4, minus leadingSpacesOfGlyph(A)=0 → 4
        //   glyphWidth(B)=4, minus overlap(A,B)=3 → 1
        //   total = 5
        // → output must not be wider than 5 visible columns
        FigletRenderer renderer = new FigletRenderer(asymmetricFont).withWidth(120);
        String result = renderer.render("AB");
        assertThat(result).isNotEmpty();
        for (String line : result.split("\n", -1)) {
            assertThat(line.length())
                .as("No output line should exceed fitted width")
                .isLessThanOrEqualTo(5);
        }
    }

    @Test
    void render_asymmetricFont_strictMode_unknownCharacter_shouldThrow() {
        FigletRenderer renderer = new FigletRenderer(asymmetricFont).setStrict(true);
        // 'C' is not defined in asymmetricFont
        assertThatThrownBy(() -> renderer.render("C"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("AsymmetricFont");
    }

    @Test
    void render_asymmetricFont_lenientMode_unknownCharacter_shouldFallbackToQuestionMark() {
        FigletRenderer renderer = new FigletRenderer(asymmetricFont).setStrict(false);
        // 'C' is not defined — falls back to '?' which IS defined in asymmetricFont
        assertThat(renderer.render("C")).isNotEmpty();
    }

    @Test
    void render_multipleWordsExceedingWidth_shouldProduceMultipleBands() {
        // width forces each word onto its own band
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(3);
        String result = renderer.render("A B C");
        long newlines = result.chars().filter(c -> c == '\n').count();
        // 3 words -> 3 bands x height(2) = 6
        assertThat(newlines).isEqualTo(3L * testFont.getHeight());
    }

    @Test
    void render_textEndingWithSpace_shouldNotThrow() {
        FigletRenderer renderer = new FigletRenderer(testFont);
        assertThat(renderer.render("A ")).isNotEmpty();
    }

    @Test
    void render_onlySpaces_shouldRenderUsingSpaceGlyph() {
        FigletRenderer renderer = new FigletRenderer(testFont);
        String result = renderer.render("  ");
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(testFont.getHeight());
    }

    @Test
    void render_zeroHeightFont_blankLine_shouldProduceNoNewlines() {
        // "\n" splits into two empty lines, each triggering appendBlankLine
        FigletFont zeroHeightFont = new FigletFont("ZeroHeight", '$', 0, 0, List.of(), Map.of());
        FigletRenderer renderer = new FigletRenderer(zeroHeightFont);
        String result = renderer.render("\n");
        assertThat(result).isEmpty();
    }

    @Test
    void render_withCustomWidth_shouldRespectConfiguredLimit() {
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(2);
        String result = renderer.render("ABCDE");
        // very small width -> each single-glyph word forced onto its own band
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isGreaterThanOrEqualTo(testFont.getHeight());
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "AB", "ABBA", "BAAB", "AABAB"})
    void render_rightToLeftFont_equalsReversedTextLeftToRight(String text) {
        // fitting two visible glyphs is symmetric, so right-to-left output equals the
        // reversed text rendered left-to-right; blank glyphs are left out on purpose,
        // since fitting after a blank glyph depends on the direction
        String reversed = new StringBuilder(text).reverse().toString();

        String result = new FigletRenderer(buildAsymmetricFont(true)).render(text);

        assertThat(result).isEqualTo(new FigletRenderer(asymmetricFont).render(reversed));
    }

    @Test
    void render_rightToLeftFontWithWrapping_keepsLinesInReadingOrder() {
        String result = new FigletRenderer(buildAsymmetricFont(true)).withWidth(1).render("AB BB");

        assertThat(result).isEqualTo(new FigletRenderer(asymmetricFont).withWidth(1).render("BA BB"));
    }

    @Test
    void mirror_glyphWithUnevenRows_padsAndReversesRows() {
        char[][] glyph = {{'a', 'b', 'c'}, {'d'}};

        assertThat(FigletRenderer.mirror(glyph)).isDeepEqualTo(new char[][] {{'c', 'b', 'a'}, {' ', ' ', 'd'}});
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "B A | ' ####' | '## #'",
        "B B | ' ####' | '####'",
        "A A | '####'  | '# #'"
    })
    void render_blankGlyphBetweenWords_shiftsAllRowsEqually(String text, String row0, String row1) {
        // the blank space glyph is fitted away; fitting the next glyph against the
        // blank glyph instead of the rendered rows used to shift rows unequally
        String result = new FigletRenderer(asymmetricFont).render(text);

        assertThat(result).isEqualTo(row0 + "\n" + row1 + "\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "A A   | '##  ##'",
        "A  A  | '##   ##'",
        "A     | '##'",
        "' A'  | ' ##'"
    })
    void render_spaceGlyphWithHardblank_keepsSpaceVisible(String text, String expectedRow) {
        // hardblanks must not be fitted away, but are printed as spaces
        FigletFont font = buildUniformFont(GLYPH_NORMAL, GLYPH_HARDBLANK_SPACE);

        String result = new FigletRenderer(font).render(text);

        assertThat(result).isEqualTo(expectedRow + "\n" + expectedRow + "\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "A A | ' ##      ##'",
        "AA  | ' ##  ##'",
        "A   | ' ##'"
    })
    void render_fullWidthFont_placesGlyphsUnfitted(String text, String expectedRow) {
        Map<Integer, char[][]> chars = Map.of((int) 'A', GLYPH_PADDED, (int) ' ', GLYPH_SPACE);
        FigletFont font = new FigletFont("FullWidth", '$', 2, 1, List.of(), chars, false, 0);

        String result = new FigletRenderer(font).render(text);

        assertThat(result).isEqualTo(expectedRow + "\n" + expectedRow + "\n");
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
        "' '; a; 128; a",
        "a; ' '; 128; a",
        "' '; a; 64; a",
        "a; b; 64;",
        "a; b; 128; b",
        "$; b; 128; b",
        "a; $; 128; a",
        "|; |; 129; |",
        "|; |; 130;",
        "_; /; 130; /",
        "/; _; 130; /",
        "|; /; 132; /",
        "[; {; 132; {",
        "{; (; 132; (",
        "(; <; 132; <",
        "/; /; 132;",
        "[; ]; 136; |",
        "); (; 136; |",
        "/; \\; 144; |",
        "\\; /; 144; Y",
        ">; <; 144; X",
        "<; >; 144;",
        "$; $; 160; $",
        "$; $; 129;"
    })
    void smush_charactersAndRules_returnExpectedCharacter(char left, char right, int layout, Character expected) {
        char result = FigletRenderer.smush(left, right, layout, '$', false);

        assertThat(result).isEqualTo(expected == null ? (char) 0 : expected);
    }

    @Test
    void smush_universalRightToLeft_leftCharacterWins() {
        assertThat(FigletRenderer.smush('a', 'b', 128, '$', true)).isEqualTo('a');
    }

    @ParameterizedTest
    @CsvSource({"false, abb", "true, bba"})
    void render_universalSmushing_laterCharacterWins(boolean rightToLeft, String expected) {
        Map<Integer, char[][]> chars = Map.of((int) 'A', new char[][] {{'a', 'a'}}, (int) 'B', new char[][] {{'b', 'b'}});
        FigletFont font = new FigletFont("Universal", '$', 1, 1, List.of(), chars, rightToLeft, FigletFont.LAYOUT_SMUSHING);

        assertThat(new FigletRenderer(font).render("AB")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest
    @CsvSource({"I, II, ||", "H, HH, |-|-|"})
    void render_equalCharacterSmushing_mergesOnlyGlyphsWiderThanOne(char ch, String text, String expected) {
        // glyphs narrower than two columns are never smushed, only fitted until they touch
        Map<Integer, char[][]> chars = Map.of((int) 'I', new char[][] {{'|'}}, (int) 'H', new char[][] {{'|', '-', '|'}});
        int layout = FigletFont.LAYOUT_SMUSHING | FigletFont.SMUSH_EQUAL;
        FigletFont font = new FigletFont("Equal", '$', 1, 1, List.of(), chars, false, layout);

        assertThat(new FigletRenderer(font).render(text)).isEqualTo(expected + "\n");
    }

    @Test
    void render_standardFont_smushesLikeFiglet() {
        String result = new FigletRenderer(FigletFontRegistry.loadFont("standard")).render("Hello");

        assertThat(result).isEqualTo(String.join("\n",
            " _   _      _ _",
            "| | | | ___| | | ___",
            "| |_| |/ _ \\ | |/ _ \\",
            "|  _  |  __/ | | (_) |",
            "|_| |_|\\___|_|_|\\___/",
            "",
            ""));
    }

    @Test
    void render_wrappedWordWiderThanWidthFollowedByText_wrapsAgain() {
        // "BBBB" moves to a new band but is still too wide on its own, so the
        // following space must start yet another band
        String result = new FigletRenderer(testFont).withWidth(6).render("A BBBB C");

        assertThat(result).isEqualTo("##\n##\n########\n########\n##\n##\n");
    }

    /**
     * Builds a font where every printable ASCII character and the seven Deutsch
     * extras map to {@code glyph}, and the space character maps to {@code spaceGlyph}.
     */
    private static FigletFont buildUniformFont(char[][] glyph, char[][] spaceGlyph) {
        Map<Integer, char[][]> chars = new LinkedHashMap<>();
        chars.put((int) ' ', spaceGlyph);
        for (int cp = 33; cp <= 126; cp++) {
            chars.put(cp, glyph);
        }
        for (int cp : new int[]{196, 214, 220, 228, 246, 252, 223}) {
            chars.put(cp, glyph);
        }
        // Also add '?' for lenient-mode fallback
        chars.putIfAbsent((int) '?', glyph);
        return new FigletFont("TestFont", '$', 2, 1, List.of(), chars);
    }

    /**
     * Builds a font with only 'A' (→ {@link #GLYPH_A}), 'B' (→ {@link #GLYPH_B}),
     * space (→ {@link #GLYPH_SPACE}), and '?' (→ {@link #GLYPH_NORMAL}).
     */
    private static FigletFont buildAsymmetricFont() {
        return buildAsymmetricFont(false);
    }

    /**
     * Builds the font described at {@link #buildAsymmetricFont()} with the given print direction.
     */
    private static FigletFont buildAsymmetricFont(boolean rightToLeft) {
        Map<Integer, char[][]> chars = Map.of(
            (int) 'A', GLYPH_A,
            (int) 'B', GLYPH_B,
            (int) ' ', GLYPH_SPACE,
            (int) '?', GLYPH_NORMAL
        );
        return new FigletFont("AsymmetricFont", '$', 2, 1, List.of(), chars, rightToLeft, FigletFont.LAYOUT_FITTING);
    }

}
