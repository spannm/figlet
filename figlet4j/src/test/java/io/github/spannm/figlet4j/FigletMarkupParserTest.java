package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Unit tests for {@link FigletMarkupParser}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletMarkupParserTest {

    @Test
    void parse_emptyString_shouldReturnEmptyList() {
        assertThat(FigletMarkupParser.parse("")).isEmpty();
    }

    @Test
    void parse_nullMarkup_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletMarkupParser.parse(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("markup");
    }

    @Test
    void parse_plainTextOnly_shouldReturnSingleTextNode() {
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse("just plain text, no tags at all");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).type()).isEqualTo(FigletMarkupNode.Type.TEXT);
        assertThat(nodes.get(0).isText()).isTrue();
        assertThat(nodes.get(0).text()).isEqualTo("just plain text, no tags at all");
    }

    @Test
    void parse_singleFontTag_shouldReturnFontNode() {
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse("<figletFont name=\"roman\">Hello</figletFont>");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).type()).isEqualTo(FigletMarkupNode.Type.FONT);
        assertThat(nodes.get(0).isFont()).isTrue();
        assertThat(nodes.get(0).fontName()).isEqualTo("roman");
        assertThat(nodes.get(0).text()).isEqualTo("Hello");
    }

    @Test
    void parse_lineBreakTag_shouldReturnLineBreakNode() {
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse("<lineBreak/>");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).type()).isEqualTo(FigletMarkupNode.Type.LINE_BREAK);
        assertThat(nodes.get(0).isLineBreak()).isTrue();
        assertThat(nodes.get(0).text()).isEmpty();
        assertThat(nodes.get(0).fontName()).isNull();
    }

    @Test
    void parse_lineBreakWithInternalWhitespace_shouldStillMatch() {
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse("<lineBreak  />");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).type()).isEqualTo(FigletMarkupNode.Type.LINE_BREAK);
        assertThat(nodes.get(0).isLineBreak()).isTrue();
    }

    @Test
    void parse_mixedSequence_shouldPreserveDocumentOrder() {
        String markup = "<figletFont name=\"roman\">${project.name}</figletFont>"
            + "<lineBreak/>"
            + "plain text below"
            + "<figletFont name=\"small\">v2</figletFont>";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).extracting(FigletMarkupNode::type).containsExactly(
            FigletMarkupNode.Type.FONT,
            FigletMarkupNode.Type.LINE_BREAK,
            FigletMarkupNode.Type.TEXT,
            FigletMarkupNode.Type.FONT);
        assertThat(nodes.get(0).text()).isEqualTo("${project.name}");
        assertThat(nodes.get(2).text()).isEqualTo("plain text below");
        assertThat(nodes.get(3).fontName()).isEqualTo("small");
    }

    @Test
    void parse_placeholderInsideFontText_shouldNotBeResolved() {
        // The parser must never touch ${...} content — that's the caller's job, after parsing.
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(
            "<figletFont name=\"roman\">${project.version}</figletFont>");

        assertThat(nodes.get(0).text()).isEqualTo("${project.version}");
    }

    @Test
    void parse_placeholderValueContainingAngleBrackets_shouldNotBreakStructure() {
        // Simulates the caller substituting a placeholder BEFORE parsing being a bad idea:
        // here we simulate the SAFE order — substitution happens only on already-isolated
        // node text after parsing — by asserting the raw literal text node is untouched
        // except for the per-line trimming applied to all plain text.
        String markup = "before <figletFont name=\"roman\">mid</figletFont> after <weird>literal</weird> tail";

        // "<weird>" and "</weird>" are not part of the known DSL and contain neither
        // 'figletFont' nor 'lineBreak' nor 'preserveWhitespace', so they must pass
        // through as literal text rather than being (mis)treated as structural markup.
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).extracting(FigletMarkupNode::type).containsExactly(
            FigletMarkupNode.Type.TEXT,
            FigletMarkupNode.Type.FONT,
            FigletMarkupNode.Type.TEXT);
        assertThat(nodes.get(0).text()).isEqualTo("before");
        assertThat(nodes.get(2).text()).isEqualTo("after <weird>literal</weird> tail");
    }

    @Test
    void parse_indentationBetweenTags_shouldBeDroppedEntirely() {
        // Simulates a pom.xml <content><![CDATA[ ... ]]></content> block, indented for readability.
        String markup = "\n    <figletFont name=\"chunky\">${project.name}</figletFont><lineBreak/>\n"
            + "    <figletFont name=\"small\">${project.version}</figletFont><lineBreak/>\n"
            + "    built by ${user.name}\n";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).extracting(FigletMarkupNode::type).containsExactly(
            FigletMarkupNode.Type.FONT,
            FigletMarkupNode.Type.LINE_BREAK,
            FigletMarkupNode.Type.FONT,
            FigletMarkupNode.Type.LINE_BREAK,
            FigletMarkupNode.Type.TEXT);
        // the whitespace-only fragments before/between the font tags produced NO text nodes at all
        assertThat(nodes.get(4).text()).isEqualTo("built by ${user.name}");
    }

    @Test
    void parse_multiLineText_shouldTrimEachLineAndDropBlankLines() {
        String markup = "  first line  \n\n   \n  second line  ";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).text()).isEqualTo("first line\nsecond line");
    }

    @Test
    void parse_onlyWhitespace_shouldProduceNoNodes() {
        assertThat(FigletMarkupParser.parse("   \n  \n\t  ")).isEmpty();
    }

    @Test
    void parse_preserveWhitespaceTag_shouldKeepContentVerbatim() {
        String markup = "<preserveWhitespace>    indented caption, whitespace preserved</preserveWhitespace>";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).type()).isEqualTo(FigletMarkupNode.Type.TEXT);
        assertThat(nodes.get(0).text()).isEqualTo("    indented caption, whitespace preserved");
    }

    @Test
    void parse_preserveWhitespaceTag_multiLine_shouldKeepIndentationAndBlankLines() {
        String markup = "<preserveWhitespace>  line one\n\n    line two  </preserveWhitespace>";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).text()).isEqualTo("  line one\n\n    line two  ");
    }

    @Test
    void parse_preserveWhitespaceTag_empty_shouldProduceNoNode() {
        assertThat(FigletMarkupParser.parse("<preserveWhitespace></preserveWhitespace>")).isEmpty();
    }

    @Test
    void parse_preserveWhitespaceMixedWithOtherTags_shouldTrimOnlyOutsideIt() {
        String markup = "  before  <preserveWhitespace>  kept  </preserveWhitespace>  <lineBreak/>  after  ";

        List<FigletMarkupNode> nodes = FigletMarkupParser.parse(markup);

        assertThat(nodes).extracting(FigletMarkupNode::type).containsExactly(
            FigletMarkupNode.Type.TEXT,
            FigletMarkupNode.Type.TEXT,
            FigletMarkupNode.Type.LINE_BREAK,
            FigletMarkupNode.Type.TEXT);
        assertThat(nodes.get(0).text()).isEqualTo("before");
        assertThat(nodes.get(1).text()).isEqualTo("  kept  ");
        assertThat(nodes.get(3).text()).isEqualTo("after");
    }

    @Test
    void parse_unclosedPreserveWhitespaceTag_shouldThrowFigletException() {
        assertThatThrownBy(() -> FigletMarkupParser.parse("<preserveWhitespace>oops, no closing tag"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("preserveWhitespace");
    }

    @Test
    void parse_unclosedFontTag_shouldThrowFigletException() {
        assertThatThrownBy(() -> FigletMarkupParser.parse("<figletFont name=\"roman\">oops, no closing tag"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("figletFont");
    }

    @Test
    void parse_fontTagMissingNameAttribute_shouldThrowFigletException() {
        assertThatThrownBy(() -> FigletMarkupParser.parse("<figletFont>Hello</figletFont>"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("figletFont");
    }

    @Test
    void parse_nonSelfClosingLineBreak_shouldThrowFigletException() {
        assertThatThrownBy(() -> FigletMarkupParser.parse("before <lineBreak> after"))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("lineBreak");
    }

    @Test
    void parse_result_shouldBeImmutable() {
        List<FigletMarkupNode> nodes = FigletMarkupParser.parse("plain text");
        assertThatThrownBy(() -> nodes.add(FigletMarkupNode.lineBreak()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

}
