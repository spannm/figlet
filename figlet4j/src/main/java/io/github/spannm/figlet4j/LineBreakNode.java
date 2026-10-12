package io.github.spannm.figlet4j;

/**
 * An explicit, manually-placed line break.
 *
 * @since 1.1.0
 */
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
