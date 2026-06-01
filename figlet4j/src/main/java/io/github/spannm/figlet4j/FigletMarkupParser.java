package io.github.spannm.figlet4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses the figlet4j markup DSL into an ordered list of {@link FigletMarkupNode}s.
 * <p>
 * The DSL recognises exactly three tags, deliberately kept flat (no nesting):
 * <ul>
 *   <li>{@code <figletFont name="...">...</figletFont>} — the enclosed text is
 *       rendered as ASCII art using the named font. The tag body is taken
 *       verbatim; it is <em>not</em> itself scanned for nested tags.</li>
 *   <li>{@code <lineBreak/>} — an explicit line break, independent of any font
 *       or automatic word-wrapping.</li>
 *   <li>{@code <preserveWhitespace>...</preserveWhitespace>} — the enclosed text
 *       is taken verbatim, exempt from the whitespace trimming described below.</li>
 * </ul>
 * Everything else becomes literal text, but — unlike the content of
 * {@code <figletFont>} and {@code <preserveWhitespace>} — is trimmed on a
 * per-line basis: leading/trailing whitespace on each line is removed (this is
 * what XML indentation between tags produces), and lines that are blank after
 * trimming are dropped entirely. This lets {@code content} be indented freely
 * in a {@code pom.xml} without that indentation leaking into the rendered
 * output. Wrap a block in {@code <preserveWhitespace>} whenever the
 * whitespace itself is meaningful (e.g. deliberately indented captions).
 * <p>
 * This is intentionally <em>not</em> HTML: none of the three tag names are
 * HTML elements, nor do they carry HTML semantics. Parsing happens strictly
 * on the raw input; this class has no notion of {@code ${...}}-style
 * placeholders. Callers that need placeholder substitution (e.g. the Maven
 * plugin resolving {@code ${project.name}}) must do so <em>after</em> parsing,
 * via {@link FigletMarkupNode#withText(String)} on each node's already-isolated
 * text content — never on the raw markup string before parsing, since a
 * substituted value containing {@code <} or {@code >} could otherwise be
 * misinterpreted as markup structure.
 *
 * @since 1.0.0
 */
public final class FigletMarkupParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "<figletFont\\s+name=\"([^\"]*)\"\\s*>(.*?)</figletFont>"
        + "|<lineBreak\\s*/>"
        + "|<preserveWhitespace\\s*>(.*?)</preserveWhitespace>",
        Pattern.DOTALL);

    /**
     * Matches any leftover fragment that looks like an attempt at one of the
     * three known tags but wasn't recognised by {@link #TOKEN_PATTERN} — e.g. an
     * unclosed {@code <figletFont>}, a missing/malformed {@code name}
     * attribute, a non-self-closing {@code <lineBreak>}, or an unclosed
     * {@code <preserveWhitespace>}. Used to turn likely typos into a clear
     * error instead of silently emitting them as literal text.
     */
    private static final Pattern SUSPICIOUS_PATTERN = Pattern.compile(
        "</?figletFont\\b|</?lineBreak\\b|</?preserveWhitespace\\b", Pattern.CASE_INSENSITIVE);

    private FigletMarkupParser() {
    }

    /**
     * Parses {@code markup} into an ordered list of nodes.
     *
     * @param markup the raw markup source; must not be {@code null}
     * @return an ordered, immutable list of nodes; empty if {@code markup} is empty
     *         or reduces to nothing after whitespace trimming
     * @throws FigletException      if a fragment looks like a malformed or unclosed
     *                              {@code <figletFont>}, {@code <lineBreak>}, or
     *                              {@code <preserveWhitespace>} tag
     * @throws NullPointerException if {@code markup} is {@code null}
     */
    public static List<FigletMarkupNode> parse(String markup) {
        if (markup == null) {
            throw new NullPointerException("markup");
        }
        if (markup.isEmpty()) {
            return List.of();
        }

        List<FigletMarkupNode> nodes = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(markup);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                addTrimmedText(nodes, markup.substring(lastEnd, matcher.start()));
            }

            String fontName = matcher.group(1);
            String preserved = matcher.group(3);
            if (fontName != null) {
                nodes.add(FigletMarkupNode.font(fontName, matcher.group(2)));
            } else if (preserved != null) {
                if (!preserved.isEmpty()) {
                    nodes.add(FigletMarkupNode.text(preserved));
                }
            } else {
                nodes.add(FigletMarkupNode.lineBreak());
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < markup.length()) {
            addTrimmedText(nodes, markup.substring(lastEnd));
        }

        return List.copyOf(nodes);
    }

    /**
     * Appends {@code text} as a literal {@link FigletMarkupNode.TextNode}, after
     * (a) checking it for tag-like fragments that were not recognised as valid
     * markup, and (b) trimming it on a per-line basis: leading/trailing
     * whitespace is removed from every line, and lines left blank after
     * trimming are dropped — see the class Javadoc. Use
     * {@code <preserveWhitespace>} instead of plain text for content where
     * this trimming is unwanted.
     *
     * @throws FigletException if {@code text} contains a suspicious fragment
     */
    private static void addTrimmedText(List<FigletMarkupNode> nodes, String text) {
        checkForSuspiciousFragment(text);

        String trimmed = trimPerLine(text);
        if (!trimmed.isEmpty()) {
            nodes.add(FigletMarkupNode.text(trimmed));
        }
    }

    /**
     * Trims leading/trailing whitespace from every line of {@code text} and
     * drops lines that are blank afterwards, rejoining the remaining lines
     * with {@code \n}.
     */
    private static String trimPerLine(String text) {
        return text.lines()
            .map(String::strip)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n"));
    }

    /**
     * Checks {@code text} for tag-like fragments that were not recognised as
     * valid markup — a strong signal of a typo (e.g. missing closing tag,
     * unquoted or missing {@code name} attribute) rather than intentional
     * literal text.
     *
     * @throws FigletException if {@code text} contains a suspicious fragment
     */
    private static void checkForSuspiciousFragment(String text) {
        Matcher suspicious = SUSPICIOUS_PATTERN.matcher(text);
        if (suspicious.find()) {
            throw new FigletException(String.format(
                "Malformed figlet markup: found '%s' that is not part of a valid "
                + "<figletFont name=\"...\">...</figletFont>, <lineBreak/>, or "
                + "<preserveWhitespace>...</preserveWhitespace> tag. "
                + "Check for a missing closing tag or a missing/unquoted 'name' attribute. "
                + "Fragment: \"%s\"",
                suspicious.group(), excerpt(text, suspicious.start())));
        }
    }

    /** Returns a short, single-line excerpt around {@code index} for error messages. */
    private static String excerpt(String text, int index) {
        int from = Math.max(0, index - 20);
        int to = Math.min(text.length(), index + 20);
        String snippet = text.substring(from, to).replace("\n", "\\n");
        return (from > 0 ? "..." : "") + snippet + (to < text.length() ? "..." : "");
    }

}
