package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.Objects;

/**
 * Text to be rendered as ASCII art with a named font, or with the renderer's
 * default font if no name is given.
 *
 * @since 1.0.1
 */
final class FontNode implements FigletMarkupNode {

    private final String fontName;
    private final String text;

    FontNode(String fontName, String text) {
        this.fontName = fontName;
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
        return Objects.equals(fontName, other.fontName) && text.equals(other.text);
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
