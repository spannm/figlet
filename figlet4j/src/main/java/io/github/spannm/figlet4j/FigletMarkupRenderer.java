package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.function.Function;

/**
 * Renders a {@link FigletMarkupParser markup} document that may combine ASCII-art
 * banners (in different fonts) with literal, unrendered text.
 * <p>
 * Unlike {@link FigletRenderer}, which always renders its entire input with a
 * single font, this class resolves fonts per {@code <figletFont name="...">}
 * node via a caller-supplied {@link Function}, typically {@link FigletFontRegistry#loadFont(String)}
 * or an equivalent lookup.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * FigletMarkupRenderer renderer = new FigletMarkupRenderer(FigletFontRegistry::loadFont)
 *     .withWidth(72)
 *     .setStrict(true);
 *
 * String banner = renderer.render(
 *     "<figletFont name=\"standard\">Hello</figletFont><lineBreak/>"
 *     + "plain text below the banner");
 * }</pre>
 *
 * <h3>Node-by-node semantics</h3>
 * <ul>
 *   <li>{@code FONT} nodes are rendered via an internal {@link FigletRenderer}
 *       for the resolved font, using this renderer's {@link #getWidth() width}
 *       and {@link #isStrict() strict} settings. Rendered banners always end in
 *       a trailing {@code \n} (see {@link FigletRenderer#render(String)}), so
 *       content that follows automatically starts on a fresh line even without
 *       an explicit {@code <lineBreak/>}.</li>
 *   <li>{@code TEXT} nodes are appended verbatim — no wrapping, no trimming.</li>
 *   <li>{@code LINE_BREAK} nodes append {@code \n}.</li>
 * </ul>
 * No line break is ever inserted implicitly beyond what {@code FigletRenderer}
 * itself produces for a banner; placement of every other break is entirely up
 * to the caller via {@code <lineBreak/>} and/or literal newlines inside text.
 *
 * @since 1.0.0
 */
public final class FigletMarkupRenderer {

    private final Function<String, FigletFont> fontResolver;
    private int     width  = FigletRenderer.DEFAULT_WIDTH;
    private boolean strict = true;

    /**
     * Constructs a markup renderer.
     *
     * @param fontResolver resolves a {@code <figletFont name="...">} attribute value
     *                     to a {@link FigletFont}; must not be {@code null}. May throw
     *                     {@link FigletException} itself (e.g. {@link FigletFontRegistry#loadFont(String)}
     *                     does); returning {@code null} is also treated as "not found".
     */
    public FigletMarkupRenderer(Function<String, FigletFont> fontResolver) {
        this.fontResolver = requireNonNull(fontResolver, "fontResolver");
    }

    /** Returns the maximum output width in characters, applied to every {@code FONT} node. */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the maximum output width in characters, applied to every {@code FONT} node.
     *
     * @param width positive integer; must be &gt;= 1
     * @return this renderer for fluent chaining
     * @throws IllegalArgumentException if {@code width} is less than 1
     */
    public FigletMarkupRenderer withWidth(int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be >= 1, got: " + width);
        }
        this.width = width;
        return this;
    }

    /** Returns {@code true} if unsupported characters in a {@code FONT} node cause a {@link FigletException}. */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Controls fail-fast behaviour for unsupported characters in {@code FONT} nodes,
     * mirroring {@link FigletRenderer#setStrict(boolean)}.
     *
     * @param strict {@code true} = throw on first unknown character in a banner;
     *               {@code false} = replace unknown characters with {@code '?'}
     * @return this renderer for fluent chaining
     */
    public FigletMarkupRenderer setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * Parses {@code markup} and renders it.
     * <p>
     * Convenience shorthand for {@code render(FigletMarkupParser.parse(markup))}.
     * Use {@link #render(List)} directly if node text needs to be transformed
     * (e.g. placeholder substitution) between parsing and rendering.
     *
     * @param markup the raw markup source; must not be {@code null}
     * @return the combined, rendered output
     * @throws FigletException if the markup is malformed, or a referenced font
     *                         cannot be resolved, or (in strict mode) a {@code FONT}
     *                         node contains an unsupported character
     */
    public String render(String markup) {
        return render(FigletMarkupParser.parse(markup));
    }

    /**
     * Renders an already-parsed node list.
     *
     * @param nodes the nodes to render, in document order; must not be {@code null}
     * @return the combined, rendered output
     * @throws FigletException if a referenced font cannot be resolved, or (in
     *                         strict mode) a {@code FONT} node contains an
     *                         unsupported character
     */
    public String render(List<FigletMarkupNode> nodes) {
        requireNonNull(nodes, "nodes");

        StringBuilder out = new StringBuilder();
        for (FigletMarkupNode node : nodes) {
            if (node.isFont()) {
                out.append(renderFontNode(node));
            } else if (node.isLineBreak()) {
                out.append("\n");
            } else {
                out.append(node.text());
            }
        }
        return out.toString();
    }

    private String renderFontNode(FigletMarkupNode node) {
        String fontName = node.fontName();
        FigletFont font;
        try {
            font = fontResolver.apply(fontName);
        } catch (FigletException ex) {
            throw new FigletException(
                "Failed to resolve font '" + fontName + "' referenced in figlet markup: " + ex.getMessage(), ex);
        }
        if (font == null) {
            throw new FigletException("Unknown font '" + fontName + "' referenced in figlet markup.");
        }

        FigletRenderer renderer = new FigletRenderer(font).withWidth(width).setStrict(strict);
        return renderer.render(node.text());
    }

}
