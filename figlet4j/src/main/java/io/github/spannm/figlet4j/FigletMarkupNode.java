package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * One node of a parsed {@link FigletMarkupParser markup} document.
 * <p>
 * A document is an ordered list of three node kinds:
 * <ul>
 *   <li>{@link Type#TEXT} — literal text, rendered verbatim (not converted to ASCII art)</li>
 *   <li>{@link Type#FONT} — text to be rendered as ASCII art using a named font</li>
 *   <li>{@link Type#LINE_BREAK} — an explicit line break, independent of any font or wrapping</li>
 * </ul>
 * <p>
 * Instances are immutable. Use {@link #withText(String)} to obtain a copy with
 * different text content — typically to substitute placeholders (e.g. Maven
 * {@code ${property}} references) <em>after</em> parsing, so that arbitrary
 * substituted values can never be reinterpreted as markup structure.
 *
 * @since 1.0.0
 */
public interface FigletMarkupNode {

    /** Discriminates the three node kinds a markup document can contain. */
    enum Type {
        TEXT,
        FONT,
        LINE_BREAK
    }

    /**
     * Creates a literal text node.
     *
     * @param text the text to render verbatim; must not be {@code null}
     * @return a new {@link Type#TEXT} node
     * @throws NullPointerException if {@code text} is {@code null}
     */
    static FigletMarkupNode text(String text) {
        return new TextNode(text);
    }

    /**
     * Creates a font (ASCII-art) node.
     *
     * @param fontName the referenced font's name, as used with a font resolver; must not be {@code null}
     * @param text     the text to render as ASCII art using that font; must not be {@code null}
     * @return a new {@link Type#FONT} node
     * @throws NullPointerException if {@code fontName} or {@code text} is {@code null}
     */
    static FigletMarkupNode font(String fontName, String text) {
        return new FontNode(fontName, text);
    }

    /**
     * Returns the single, shared {@link Type#LINE_BREAK} node instance.
     *
     * @return the line-break node
     */
    static FigletMarkupNode lineBreak() {
        return LineBreakNode.INSTANCE;
    }

    /** Returns which kind of node this is. */
    Type type();

    /**
     * Checks whether this node is a literal text node.
     *
     * @return {@code true} if this node is of type {@link Type#TEXT}, {@code false} otherwise
     */
    default boolean isText() {
        return Type.TEXT == type();
    }

    /**
     * Checks whether this node is a font (ASCII-art) node.
     *
     * @return {@code true} if this node is of type {@link Type#FONT}, {@code false} otherwise
     */
    default boolean isFont() {
        return Type.FONT == type();
    }

    /**
     * Checks whether this node is a line-break node.
     *
     * @return {@code true} if this node is of type {@link Type#LINE_BREAK}, {@code false} otherwise
     */
    default boolean isLineBreak() {
        return Type.LINE_BREAK == type();
    }

    /**
     * Returns this node's text content.
     *
     * @return the text for {@link Type#TEXT} and {@link Type#FONT} nodes;
     *         an empty string for {@link Type#LINE_BREAK}
     */
    String text();

    /**
     * Returns the referenced font name.
     *
     * @return the font name for {@link Type#FONT} nodes; {@code null} otherwise
     */
    default String fontName() {
        return null;
    }

    /**
     * Returns a copy of this node with its text content replaced.
     * <p>
     * Has no effect on {@link Type#LINE_BREAK} nodes, which carry no text and
     * are returned unchanged.
     *
     * @param newText replacement text; must not be {@code null} (except for {@link Type#LINE_BREAK})
     * @return a node of the same kind with the new text
     */
    FigletMarkupNode withText(String newText);

    /** Literal, unrendered text. */
    final class TextNode implements FigletMarkupNode {
        private final String text;

        TextNode(String text) {
            this.text = requireNonNull(text, "text");
        }

        @Override
        public Type type() {
            return Type.TEXT;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public FigletMarkupNode withText(String newText) {
            return new TextNode(newText);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof TextNode)) {
                return false;
            }
            TextNode other = (TextNode) obj;
            return text.equals(other.text);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                + "[" + text + "]";
        }
    }

    /** Text to be rendered as ASCII art with a named font. */
    final class FontNode implements FigletMarkupNode {
        private final String fontName;
        private final String text;

        FontNode(String fontName, String text) {
            this.fontName = requireNonNull(fontName, "fontName");
            this.text = requireNonNull(text, "text");
        }

        @Override
        public Type type() {
            return Type.FONT;
        }

        @Override
        public String text() {
            return text;
        }

        @Override
        public String fontName() {
            return fontName;
        }

        @Override
        public FigletMarkupNode withText(String newText) {
            return new FontNode(fontName, newText);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof FontNode)) {
                return false;
            }
            FontNode other = (FontNode) obj;
            return fontName.equals(other.fontName) && text.equals(other.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fontName, text);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                + "[" + fontName + ": " + text + "]";
        }
    }

    /** An explicit, manually-placed line break. */
    final class LineBreakNode implements FigletMarkupNode {
        static final LineBreakNode INSTANCE = new LineBreakNode();

        private LineBreakNode() {
        }

        @Override
        public Type type() {
            return Type.LINE_BREAK;
        }

        @Override
        public String text() {
            return "";
        }

        @Override
        public FigletMarkupNode withText(String newText) {
            return this;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[]";
        }
    }

}
