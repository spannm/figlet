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

    @Test
    void render_explicitNewline_shouldProduceTwoBands() {
        String result = new FigletRenderer(testFont).render("A\nB");
        long newlines = result.chars().filter(c -> c == '\n').count();
        // Two bands × height(2) = 4 newlines
        assertThat(newlines).isEqualTo(2L * testFont.getHeight());
    }

    @Test
    void render_singleNewline_shouldProduceTwoBlankBands() {
        // "\n" → split gives ["", ""] → 2 empty input lines → 2 blank bands × height(2) = 4
        String result = new FigletRenderer(testFont).render("\n");
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(2L * testFont.getHeight());
    }

    @Test
    void render_twoNewlines_shouldProduceThreeBlankBands() {
        // "\n\n" → split gives ["", "", ""] → 3 empty input lines → 3 × height(2) = 6
        String result = new FigletRenderer(testFont).render("\n\n");
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(3L * testFont.getHeight());
    }

    @Test
    void render_textWithMultipleNewlines_shouldRenderEachLineIndependently() {
        String result = new FigletRenderer(testFont).render("A\n\nB");
        // 'A' band + blank band + 'B' band = 3 × height rows
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isEqualTo(3L * testFont.getHeight());
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
        // width intentionally small to force wrapping between words
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(5);
        String result = renderer.render("A B");
        // Two words → at least 2 bands
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isGreaterThanOrEqualTo(2L * testFont.getHeight());
    }

    @Test
    void render_wordWrapping_shouldNeverExceedConfiguredWidth() {
        // Regression test: the wrap-width check must measure the real fitted
        // width, not sum a per-glyph estimate. The fitting overlap between two
        // glyphs can exceed either glyph's own width (here: GLYPH_NORMAL's 2
        // trailing spaces + GLYPH_SPACE's fully blank 4-column row), which an
        // additive per-glyph estimate cannot represent — it previously judged
        // "A B" to fit within width 5 even though its real fitted width is 7.
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(5);
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

    @Test
    void fittingOverlap_identicalGlyphs_shouldComputeCorrectOverlap() {
        // GLYPH_NORMAL: trailing=2, leading=0 → overlap = 2+0-1 = 1
        int overlap = FigletRenderer.fittingOverlap(GLYPH_NORMAL, GLYPH_NORMAL);
        assertThat(overlap).isEqualTo(1);
    }

    @Test
    void fittingOverlap_paddedGlyph_shouldComputeCorrectOverlap() {
        // left trailing=1, right leading=1 → 1+1-1 = 1
        int overlap = FigletRenderer.fittingOverlap(GLYPH_PADDED, GLYPH_PADDED);
        assertThat(overlap).isEqualTo(1);
    }

    @Test
    void fittingOverlap_normalThenPadded_shouldUseMinimumAcrossRows() {
        // GLYPH_A row0: trailing=2, GLYPH_B row0: leading=2 → 2+2-1=3
        // GLYPH_A row1: trailing=3, GLYPH_B row1: leading=1 → 3+1-1=3
        // min(3,3) = 3
        int overlap = FigletRenderer.fittingOverlap(GLYPH_A, GLYPH_B);
        assertThat(overlap).isEqualTo(3);
    }

    @Test
    void fittingOverlap_noWhitespace_shouldReturnZero() {
        // Glyphs with no trailing/leading spaces: overlap = 0+0-1 = -1 → clamped to 0
        char[][] solid = {{'#', '#'}, {'#', '#'}};
        int overlap = FigletRenderer.fittingOverlap(solid, solid);
        assertThat(overlap).isZero();
    }

    @Test
    void fittingOverlap_constrainedByNarrowestRow_shouldReturnMinimum() {
        char[][] left  = {{'#', ' ', ' '}, {'#', '#', '#'}};  // trailing: 2, 0
        char[][] right = {{' ', ' ', '#'}, {' ', ' ', '#'}};  // leading:  2, 2
        // row0: 2+2-1=3, row1: 0+2-1=1 → min=1
        int overlap = FigletRenderer.fittingOverlap(left, right);
        assertThat(overlap).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "''      | 0",
        "'#'     | 0",
        "' '     | 1",
        "'# '    | 1",
        "'#  '   | 2",
        "'  #  ' | 2",
        "'   '   | 3"
    })
    void trailingSpaces_variousInputs_shouldCountCorrectly(String input, int expected) {
        assertThat(FigletRenderer.trailingSpaces(input.toCharArray())).isEqualTo(expected);
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
    void leadingSpacesOfGlyph_nullGlyph_shouldReturnZero() {
        assertThat(FigletRenderer.leadingSpacesOfGlyph(null)).isZero();
    }

    @Test
    void leadingSpacesOfGlyph_emptyGlyph_shouldReturnZero() {
        assertThat(FigletRenderer.leadingSpacesOfGlyph(new char[0][])).isZero();
    }

    @Test
    void leadingSpacesOfGlyph_uniformLeading_shouldReturnMinimum() {
        // Both rows have 1 leading space → min = 1
        assertThat(FigletRenderer.leadingSpacesOfGlyph(GLYPH_PADDED)).isEqualTo(1);
    }

    @Test
    void leadingSpacesOfGlyph_asymmetricLeading_shouldReturnMinimum() {
        // GLYPH_B row0: 2 leading, row1: 1 leading → min = 1
        assertThat(FigletRenderer.leadingSpacesOfGlyph(GLYPH_B)).isEqualTo(1);
    }

    @Test
    void leadingSpacesOfGlyph_noLeadingSpaces_shouldReturnZero() {
        assertThat(FigletRenderer.leadingSpacesOfGlyph(GLYPH_NORMAL)).isZero();
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
    void fittingOverlap_emptyGlyphRows_shouldReturnZero() {
        char[][] empty = new char[0][];
        int overlap = FigletRenderer.fittingOverlap(empty, empty);
        assertThat(overlap).isZero();
    }

    @Test
    void render_withCustomWidth_shouldRespectConfiguredLimit() {
        FigletRenderer renderer = new FigletRenderer(testFont).withWidth(2);
        String result = renderer.render("ABCDE");
        // very small width -> each single-glyph word forced onto its own band
        long newlines = result.chars().filter(c -> c == '\n').count();
        assertThat(newlines).isGreaterThanOrEqualTo(testFont.getHeight());
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
        Map<Integer, char[][]> chars = Map.of(
            (int) 'A', GLYPH_A,
            (int) 'B', GLYPH_B,
            (int) ' ', GLYPH_SPACE,
            (int) '?', GLYPH_NORMAL
        );
        return new FigletFont("AsymmetricFont", '$', 2, 1, List.of(), chars);
    }

}
