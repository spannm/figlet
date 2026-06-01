package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FigletMarkupNode} and its {@code TextNode}, {@code FontNode}
 * and {@code LineBreakNode} implementations.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletMarkupNodeTest {

    @Test
    void text_nullText_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletMarkupNode.text(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("text");
    }

    @Test
    void text_shouldReturnTextTypeNode() {
        FigletMarkupNode node = FigletMarkupNode.text("hello");

        assertThat(node.type()).isEqualTo(FigletMarkupNode.Type.TEXT);
        assertThat(node.isText()).isTrue();
        assertThat(node.isFont()).isFalse();
        assertThat(node.isLineBreak()).isFalse();
        assertThat(node.text()).isEqualTo("hello");
        assertThat(node.fontName()).isNull();
    }

    @Test
    void text_withText_shouldReturnNewTextNodeWithReplacedText() {
        FigletMarkupNode node = FigletMarkupNode.text("hello");
        FigletMarkupNode replaced = node.withText("world");

        assertThat(replaced.type()).isEqualTo(FigletMarkupNode.Type.TEXT);
        assertThat(replaced.text()).isEqualTo("world");
        assertThat(node.text()).isEqualTo("hello");
    }

    @Test
    void textNode_equals_shouldFollowValueSemantics() {
        FigletMarkupNode a = FigletMarkupNode.text("hello");
        FigletMarkupNode b = FigletMarkupNode.text("hello");
        FigletMarkupNode c = FigletMarkupNode.text("other");

        assertThat(a.equals(a)).as("reflexive").isTrue();
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("hello");
        assertThat(a).isNotEqualTo(FigletMarkupNode.font("f", "hello"));
    }

    @Test
    void textNode_hashCode_shouldBeConsistentWithEquals() {
        FigletMarkupNode a = FigletMarkupNode.text("hello");
        FigletMarkupNode b = FigletMarkupNode.text("hello");

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.hashCode()).isEqualTo("hello".hashCode());
    }

    @Test
    void textNode_toString_shouldContainClassNameAndText() {
        FigletMarkupNode node = FigletMarkupNode.text("hello");

        assertThat(node.toString()).isEqualTo("TextNode[hello]");
    }

    @Test
    void font_nullFontName_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletMarkupNode.font(null, "hello"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontName");
    }

    @Test
    void font_nullText_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletMarkupNode.font("roman", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("text");
    }

    @Test
    void font_shouldReturnFontTypeNode() {
        FigletMarkupNode node = FigletMarkupNode.font("roman", "hello");

        assertThat(node.type()).isEqualTo(FigletMarkupNode.Type.FONT);
        assertThat(node.isFont()).isTrue();
        assertThat(node.isText()).isFalse();
        assertThat(node.isLineBreak()).isFalse();
        assertThat(node.text()).isEqualTo("hello");
        assertThat(node.fontName()).isEqualTo("roman");
    }

    @Test
    void font_withText_shouldReturnNewFontNodeWithReplacedTextAndSameFontName() {
        FigletMarkupNode node = FigletMarkupNode.font("roman", "hello");
        FigletMarkupNode replaced = node.withText("world");

        assertThat(replaced.type()).isEqualTo(FigletMarkupNode.Type.FONT);
        assertThat(replaced.fontName()).isEqualTo("roman");
        assertThat(replaced.text()).isEqualTo("world");
        assertThat(node.text()).isEqualTo("hello");
    }

    @Test
    void fontNode_equals_shouldFollowValueSemantics() {
        FigletMarkupNode a = FigletMarkupNode.font("roman", "hello");
        FigletMarkupNode b = FigletMarkupNode.font("roman", "hello");
        FigletMarkupNode differentText = FigletMarkupNode.font("roman", "other");
        FigletMarkupNode differentFont = FigletMarkupNode.font("small", "hello");

        assertThat(a.equals(a)).as("reflexive").isTrue();
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(differentText);
        assertThat(a).isNotEqualTo(differentFont);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("roman");
        assertThat(a).isNotEqualTo(FigletMarkupNode.text("hello"));
    }

    @Test
    void fontNode_hashCode_shouldBeConsistentWithEquals() {
        FigletMarkupNode a = FigletMarkupNode.font("roman", "hello");
        FigletMarkupNode b = FigletMarkupNode.font("roman", "hello");

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void fontNode_toString_shouldContainClassNameFontNameAndText() {
        FigletMarkupNode node = FigletMarkupNode.font("roman", "hello");

        assertThat(node.toString()).isEqualTo("FontNode[roman: hello]");
    }

    @Test
    void lineBreak_shouldReturnLineBreakTypeNode() {
        FigletMarkupNode node = FigletMarkupNode.lineBreak();

        assertThat(node.type()).isEqualTo(FigletMarkupNode.Type.LINE_BREAK);
        assertThat(node.isLineBreak()).isTrue();
        assertThat(node.isText()).isFalse();
        assertThat(node.isFont()).isFalse();
        assertThat(node.text()).isEmpty();
        assertThat(node.fontName()).isNull();
    }

    @Test
    void lineBreak_shouldReturnSharedSingletonInstance() {
        assertThat(FigletMarkupNode.lineBreak()).isSameAs(FigletMarkupNode.lineBreak());
    }

    @Test
    void lineBreakNode_withText_shouldReturnSameInstanceUnchanged() {
        FigletMarkupNode node = FigletMarkupNode.lineBreak();

        assertThat(node.withText("ignored")).isSameAs(node);
    }

    @Test
    void lineBreakNode_toString_shouldContainClassName() {
        assertThat(FigletMarkupNode.lineBreak().toString()).isEqualTo("LineBreakNode[]");
    }

}
