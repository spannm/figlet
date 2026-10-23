package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
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
 * <h2>Usage</h2>
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
 * <h2>Node-by-node semantics</h2>
 * <ul>
 *   <li>{@code FONT} nodes are rendered via an internal {@link FigletRenderer}
 *       for the resolved font (or the {@link #withDefaultFont(FigletFont) default font}
 *       if the node has no font name), using this renderer's {@link #getWidth() width}
 *       and {@link #isStrict() strict} settings, and its
 *       {@link #withUnsupportedHandler(BiConsumer) unsupported handler}. Rendered banners always end in
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

    private final Function<String, FigletFont>   fontResolver;
    private FigletFont                           defaultFont;
    private int                                  width              = FigletRenderer.DEFAULT_WIDTH;
    private boolean                              strict             = true;
    private BiConsumer<FigletFont, Set<Integer>> unsupportedHandler = (font, codepoints) -> { };

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

    /**
     * Returns the font used for {@code FONT} nodes without a font name.
     *
     * @return the default font, or {@code null} if none was set
     * @since 1.1.0
     */
    public FigletFont getDefaultFont() {
        return defaultFont;
    }

    /**
     * Sets the font used for {@code FONT} nodes without a font name, i.e.
     * {@code <figletFont>...</figletFont>} without a {@code name} attribute.
     * Such nodes cannot be rendered unless a default font is set.
     *
     * @param defaultFont the default font; may be {@code null} to unset it
     * @return this renderer for fluent chaining
     * @since 1.1.0
     */
    public FigletMarkupRenderer withDefaultFont(FigletFont defaultFont) {
        this.defaultFont = defaultFont;
        return this;
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
            throw new IllegalArgumentException(String.format("width must be >= 1, got: %d", width));
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
     * Sets a handler that is called in lenient mode for every {@code FONT} node
     * containing characters its font cannot render, mirroring
     * {@link FigletRenderer#withUnsupportedHandler(BiConsumer)}.
     *
     * @param handler the handler; must not be {@code null}
     * @return this renderer for fluent chaining
     * @since 1.2.0
     */
    public FigletMarkupRenderer withUnsupportedHandler(BiConsumer<FigletFont, Set<Integer>> handler) {
        this.unsupportedHandler = requireNonNull(handler, "handler");
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

    /**
     * Renders a single {@code FONT} node with its named font, or with the
     * default font if the node has no font name.
     *
     * @param node the font node to render
     * @return the rendered banner
     * @throws FigletException if the font cannot be resolved
     */
    private String renderFontNode(FigletMarkupNode node) {
        String fontName = node.fontName();
        if (fontName == null) {
            if (defaultFont == null) {
                throw new FigletException(
                    "Figlet markup contains a <figletFont> tag without a 'name' attribute, but no default font is set.");
            }
            return renderWith(defaultFont, node.text());
        }

        FigletFont font;
        try {
            font = fontResolver.apply(fontName);
        } catch (FigletException ex) {
            throw new FigletException(
                String.format("Failed to resolve font '%s' referenced in figlet markup: %s", fontName, ex.getMessage()), ex);
        }
        if (font == null) {
            throw new FigletException(String.format("Unknown font '%s' referenced in figlet markup.", fontName));
        }

        return renderWith(font, node.text());
    }

    /**
     * Renders {@code text} with {@code font} using this renderer's width, strict and handler settings.
     *
     * @param font the font to render with
     * @param text the text to render
     * @return the rendered banner
     */
    private String renderWith(FigletFont font, String text) {
        return new FigletRenderer(font)
            .withWidth(width)
            .setStrict(strict)
            .withUnsupportedHandler(unsupportedHandler)
            .render(text);
    }

}
