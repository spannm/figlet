package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FigletMarkupRenderer}.
 * <p>
 * Uses small in-memory test fonts (no dependency on bundled {@code .flf} resources),
 * following the same approach as {@link FigletRendererTest}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletMarkupRendererTest {

    /** 2x2 glyph used for every printable character in the "roman" test font. */
    private static final char[][] GLYPH_ROMAN = {
        {'#', '#'},
        {'#', '#'}
    };

    /** 1x1 glyph used for every printable character in the "small" test font. */
    private static final char[][] GLYPH_SMALL = {
        {'+'},
        {'+'}
    };

    private static final FigletFont ROMAN_FONT = buildFont("roman", GLYPH_ROMAN, 2);
    private static final FigletFont SMALL_FONT = buildFont("small", GLYPH_SMALL, 1);

    private static final Map<String, FigletFont> FONTS = Map.of(
        "roman", ROMAN_FONT,
        "small", SMALL_FONT
    );

    private final FigletMarkupRenderer renderer = new FigletMarkupRenderer(FONTS::get);

    @Test
    void render_fontNodeOnly_shouldProduceAsciiArtEndingInNewline() {
        String result = renderer.render("<figletFont name=\"roman\">A</figletFont>");

        assertThat(result).isEqualTo(new FigletRenderer(ROMAN_FONT).render("A"));
        assertThat(result).endsWith("\n");
    }

    @Test
    void render_textNodeOnly_shouldBeAppendedVerbatim() {
        String result = renderer.render("just plain text");

        assertThat(result).isEqualTo("just plain text");
    }

    @Test
    void render_fontFollowedByPlainText_shouldStartOnFreshLineWithoutExplicitBreak() {
        // FigletRenderer.render() already ends its output in '\n', so no <lineBreak/> is needed
        // for the plain text to visually start on its own line.
        String result = renderer.render("<figletFont name=\"roman\">A</figletFont>plain");

        String expectedBanner = new FigletRenderer(ROMAN_FONT).render("A");
        assertThat(result).isEqualTo(expectedBanner + "plain");
    }

    @Test
    void render_explicitLineBreak_shouldInsertExactlyOneNewline() {
        String result = renderer.render("before<lineBreak/>after");

        assertThat(result).isEqualTo("before\nafter");
    }

    @Test
    void render_mixedDocument_shouldConcatenateInOrder() {
        String markup = "<figletFont name=\"roman\">A</figletFont>"
            + "<lineBreak/>"
            + "caption text"
            + "<lineBreak/>"
            + "<figletFont name=\"small\">B</figletFont>";

        String result = renderer.render(markup);

        String expected = new FigletRenderer(ROMAN_FONT).render("A")
            + "\n"
            + "caption text"
            + "\n"
            + new FigletRenderer(SMALL_FONT).render("B");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void render_respectsConfiguredWidth() {
        FigletMarkupRenderer narrow = new FigletMarkupRenderer(FONTS::get).withWidth(2);
        String result = narrow.render("<figletFont name=\"roman\">AAAA</figletFont>");

        String expected = new FigletRenderer(ROMAN_FONT).withWidth(2).render("AAAA");
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void render_unknownFontName_shouldThrowFigletException() {
        assertThatThrownBy(() -> renderer.render("<figletFont name=\"doesNotExist\">x</figletFont>"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("doesNotExist");
    }

    @Test
    void render_resolverThrows_shouldWrapWithContext() {
        FigletMarkupRenderer throwing = new FigletMarkupRenderer(name -> {
            throw new FigletException("boom");
        });

        assertThatThrownBy(() -> throwing.render("<figletFont name=\"roman\">x</figletFont>"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("roman")
            .hasMessageContaining("boom");
    }

    @Test
    void render_strictModeUnsupportedChar_shouldThrow() {
        assertThatThrownBy(() -> renderer.render("<figletFont name=\"roman\">\u00e9</figletFont>"))
            .isInstanceOf(FigletException.class);
    }

    @Test
    void render_nonStrictModeUnsupportedChar_shouldSubstituteFallback() {
        FigletMarkupRenderer lenient = new FigletMarkupRenderer(FONTS::get).setStrict(false);
        String result = lenient.render("<figletFont name=\"roman\">\u00e9</figletFont>");

        assertThat(result).isEqualTo(new FigletRenderer(ROMAN_FONT).setStrict(false).render("\u00e9"));
    }

    @Test
    void render_preParsedNodesWithSubstitutedText_shouldReflectSubstitution() {
        // Simulates the safe placeholder-substitution order: parse first, THEN
        // substitute text on the already-isolated node, THEN render.
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(
            "<figletFont name=\"roman\">${name}</figletFont>");

        List<FigletMarkupNode> substituted = nodes.stream()
            .map(n -> n.withText(n.text().replace("${name}", "A")))
            .collect(java.util.stream.Collectors.toList());

        String result = renderer.render(substituted);

        assertThat(result).isEqualTo(new FigletRenderer(ROMAN_FONT).render("A"));
    }

    @Test
    void render_nullNodes_shouldThrowNpe() {
        assertThatThrownBy(() -> renderer.render((List<FigletMarkupNode>) null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void withWidth_belowOne_shouldThrow() {
        assertThatThrownBy(() -> renderer.withWidth(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getWidth_defaultsToFigletRendererDefaultWidth() {
        assertThat(renderer.getWidth()).isEqualTo(FigletRenderer.DEFAULT_WIDTH);
    }

    @Test
    void getWidth_afterWithWidth_reflectsConfiguredValue() {
        FigletMarkupRenderer configured = new FigletMarkupRenderer(FONTS::get).withWidth(42);

        assertThat(configured.getWidth()).isEqualTo(42);
    }

    @Test
    void isStrict_defaultsToTrue() {
        assertThat(renderer.isStrict()).isTrue();
    }

    @Test
    void isStrict_afterSetStrictFalse_reflectsConfiguredValue() {
        FigletMarkupRenderer lenient = new FigletMarkupRenderer(FONTS::get).setStrict(false);

        assertThat(lenient.isStrict()).isFalse();
    }

    @Test
    void constructor_nullFontResolver_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new FigletMarkupRenderer(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontResolver");
    }

    private static FigletFont buildFont(String name, char[][] glyph, int height) {
        Map<Integer, char[][]> chars = new LinkedHashMap<>();
        char[][] spaceGlyph = new char[height][1];
        for (char[] row : spaceGlyph) {
            row[0] = ' ';
        }
        chars.put((int) ' ', spaceGlyph);
        for (int cp = 33; cp <= 126; cp++) {
            chars.put(cp, glyph);
        }
        for (int cp : new int[]{196, 214, 220, 228, 246, 252, 223}) {
            chars.put(cp, glyph);
        }
        chars.putIfAbsent((int) '?', glyph);
        return new FigletFont(name, '$', height, height - 1, List.of(), chars);
    }

}
