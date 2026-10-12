package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

/**
 * Literal, unrendered text.
 *
 * @since 1.1.0
 */
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
