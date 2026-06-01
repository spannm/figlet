package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a string of text as multi-line ASCII art using a {@link FigletFont}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * FigletFont font  = FigletFontLoader.loadBuiltin("standard");
 * FigletRenderer r = new FigletRenderer(font);
 * String banner    = r.render("Hello World");
 * System.out.println(banner);
 * }</pre>
 *
 * <h2>Multi-line input</h2>
 * Input text may contain {@code \n} line breaks. Each logical line is rendered
 * and wrapped independently.
 *
 * <h2>Word wrapping</h2>
 * When the rendered width of a line exceeds {@link #getWidth()}, the renderer
 * inserts a line break at the last space boundary before the overflow. If a
 * single word is too wide to fit, it is rendered on its own line (no mid-glyph
 * split is performed).
 *
 * <h2>Kerning / fitting</h2>
 * Adjacent glyphs are moved together until they touch: the trailing spaces of
 * the left glyph and the leading spaces of the right glyph are both removed,
 * leaving exactly one space of padding between any two visible columns.
 * Full smushing (merging compatible border characters) is not yet implemented.
 *
 * <h2>Unsupported characters (fail-fast)</h2>
 * By default, any code point not present in the font causes a
 * {@link FigletException}. Set {@link #setStrict(boolean) strict=false} to
 * replace unknown code points with {@code '?'} instead.
 *
 * @author Markus Spann
 * @since 1.0.0
 */
public final class FigletRenderer {

    /** Default terminal/output width in characters. Matches the classic FIGlet default. */
    public static final int  DEFAULT_WIDTH = 72;

    private final FigletFont font;
    private int              width         = DEFAULT_WIDTH;
    private boolean          strict        = true;

    /**
     * Constructs a renderer for the given font.
     *
     * @param font the FIGfont to use for rendering; must not be {@code null}
     */
    public FigletRenderer(FigletFont font) {
        this.font = requireNonNull(font, "font");
    }

    /**
     * Returns the maximum output width in characters.
     *
     * @return output width (default: {@value #DEFAULT_WIDTH})
     */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the maximum output width in characters.
     * Lines wider than this value are wrapped at word boundaries.
     *
     * @param width positive integer; must be &gt;= 1
     * @return this renderer for fluent chaining
     * @throws IllegalArgumentException if {@code width} is less than 1
     */
    public FigletRenderer withWidth(int width) {
        if (width < 1) {
            throw new IllegalArgumentException("width must be >= 1, got: " + width);
        }
        this.width = width;
        return this;
    }

    /**
     * Returns {@code true} if unknown characters cause a {@link FigletException}
     * (the default), or {@code false} if they are silently replaced by {@code '?'}.
     *
     * @return {@code true} when strict mode is active
     */
    public boolean isStrict() {
        return strict;
    }

    /**
     * Controls fail-fast behaviour for unsupported characters.
     *
     * @param strict {@code true} = throw {@link FigletException} on first unknown character;
     *               {@code false} = replace unknown characters with {@code '?'}
     * @return this renderer for fluent chaining
     */
    public FigletRenderer setStrict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * Renders {@code text} as ASCII art.
     * <p>
     * Each logical input line (separated by {@code \n}) is rendered independently.
     * Long lines are word-wrapped at {@link #getWidth()} character columns.
     *
     * @param text the text to render; may contain {@code \n} for explicit line breaks;
     *             must not be {@code null}
     * @return multi-line ASCII art string with lines separated by {@code \n};
     *         returns an empty string for empty input
     * @throws FigletException      if {@link #isStrict()} is active and the text contains
     *                              a character not present in the font
     * @throws NullPointerException if {@code text} is {@code null}
     */
    public String render(String text) {
        requireNonNull(text, "text");

        if (text.isEmpty()) {
            return "";
        }

        // pre-validate in strict mode before any output is produced
        if (strict) {
            validateCodepoints(text);
        }

        StringBuilder out = new StringBuilder();
        for (String inputLine : text.split("\r?\n", -1)) {
            List<String> words = splitIntoWords(inputLine);
            renderWords(words, out);
        }
        return out.toString();
    }

    /**
     * Validates that every code point in {@code text} is supported by the font.
     *
     * @param text the text to validate
     * @throws FigletException at the first unsupported code point
     */
    private void validateCodepoints(String text) {
        text.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\r') {
                return; // line breaks are structural, not rendered as glyphs
            }
            if (!font.supportsCodepoint(cp)) {
                throw new FigletException(String.format(
                    "Character '%s' (U+%04X) is not supported by font '%s'. "
                    + "Use a different font, or set strict=false to replace unsupported characters.",
                    cp < 32 ? "\\u" + Integer.toHexString(cp) : String.valueOf((char) cp),
                    cp, font.getName()));
            }
        });
    }

    /**
     * Splits a single input line into word tokens.
     * <p>
     * Space characters are emitted as individual {@code " "} tokens so the
     * renderer can decide where to insert line breaks. All other code points are
     * accumulated into word tokens.
     *
     * @param line one logical input line (no {@code \n})
     * @return ordered list of word and space tokens; never {@code null}
     */
    private static List<String> splitIntoWords(String line) {
        if (line == null || line.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < line.length();) {
            int cp = line.codePointAt(i);
            if (cp == ' ') {
                if (word.length() > 0) {
                    tokens.add(word.toString());
                    word.setLength(0);
                }
                tokens.add(" ");
            } else {
                word.appendCodePoint(cp);
            }
            i += Character.charCount(cp);
        }
        if (word.length() > 0) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    /**
     * Renders a list of word tokens into {@code out}, inserting band breaks
     * whenever the accumulated rendered width would exceed {@link #width}.
     * <p>
     * Leading space tokens at the start of a new wrapped line are discarded.
     * <p>
     * The wrap decision is made by actually building the candidate band (via
     * {@link #buildRows}) and measuring its real width, rather than by summing
     * a per-glyph width estimate: the fitting overlap between two glyphs can
     * exceed either glyph's own width (e.g. a wide space glyph following a
     * glyph with few trailing spaces), a case a simple additive estimate
     * cannot represent without diverging from what {@link #buildRows} actually
     * produces. Measuring the real, tentative band keeps the wrap decision and
     * the final output — {@link #flushLine} builds the same way — in agreement
     * by construction.
     *
     * @param words word and space tokens for one logical input line
     * @param out   output buffer to append rendered rows to
     */
    private void renderWords(List<String> words, StringBuilder out) {
        if (words.isEmpty()) {
            appendBlankLine(out);
            return;
        }

        List<String> currentLine = new ArrayList<>();

        for (String token : words) {
            List<String> candidate = new ArrayList<>(currentLine);
            candidate.add(token);

            if (!currentLine.isEmpty() && renderedWidth(candidate) > width) {
                flushLine(currentLine, out);
                currentLine.clear();
                if (" ".equals(token)) {
                    continue; // discard leading space on wrapped line
                }
                candidate = List.of(token);
            }
            currentLine = candidate;
        }
        if (!currentLine.isEmpty()) {
            flushLine(currentLine, out);
        }
    }

    /**
     * Returns the real rendered width (in columns) of {@code tokens}, i.e. the
     * widest of its rows after trimming trailing spaces — the same measure
     * {@link #flushLine} produces in its output.
     *
     * @param tokens word and space tokens forming one candidate band
     * @return widest trimmed row width in columns; 0 for a band with no visible content
     */
    private int renderedWidth(List<String> tokens) {
        int max = 0;
        for (StringBuilder row : buildRows(tokens)) {
            max = Math.max(max, trimmedLength(row));
        }
        return max;
    }

    /**
     * Renders a list of word tokens as one horizontal ASCII art band into {@code out}.
     * <p>
     * Adjacent glyphs are fitted: their shared whitespace border is collapsed so
     * that visible columns from neighbouring glyphs are exactly one space apart.
     * The leading whitespace of the first glyph in the band is stripped so that
     * the banner starts flush at column zero.
     *
     * @param tokens word and space tokens forming one visual line
     * @param out    output buffer to append rendered rows to
     */
    private void flushLine(List<String> tokens, StringBuilder out) {
        writeRows(buildRows(tokens), out);
    }

    /**
     * Builds the per-row character buffers for one band of {@code tokens},
     * fitting each resolved glyph against the previous one exactly as the
     * final rendered output will. Shared by {@link #flushLine} (which writes
     * the result) and {@link #renderedWidth} (which only measures it), so the
     * wrap decision can never diverge from what actually gets rendered.
     *
     * @param tokens word and space tokens forming one band
     * @return one {@link StringBuilder} per font row, holding the fitted glyphs
     */
    private StringBuilder[] buildRows(List<String> tokens) {
        int height = font.getHeight();
        StringBuilder[] rows = new StringBuilder[height];
        for (int r = 0; r < height; r++) {
            rows[r] = new StringBuilder();
        }

        char[][] prevGlyph = null;

        for (String token : tokens) {
            for (int codepoint : token.codePoints().toArray()) {
                char[][] glyph = resolveGlyph(codepoint);
                if (glyph == null) {
                    continue;
                }

                if (prevGlyph == null) {
                    appendFirstGlyph(glyph, rows, height);
                } else {
                    appendFittedGlyph(glyph, prevGlyph, rows, height);
                }

                prevGlyph = glyph;
            }
        }

        return rows;
    }

    /**
     * Appends the first glyph of a band to {@code rows}, stripping uniform
     * leading whitespace so the output starts flush at column zero.
     *
     * @param glyph  glyph row data
     * @param rows   per-row output buffers
     * @param height number of font rows
     */
    private static void appendFirstGlyph(char[][] glyph, StringBuilder[] rows, int height) {
        int skip = leadingSpacesOfGlyph(glyph);
        for (int r = 0; r < height; r++) {
            char[] row = r < glyph.length ? glyph[r] : new char[0];
            int s = Math.min(skip, row.length);
            rows[r].append(row, s, row.length - s);
        }
    }

    /**
     * Appends a subsequent glyph to {@code rows} using fitting overlap against
     * the previous glyph.
     * <p>
     * For each row:
     * <ol>
     *   <li>Trailing spaces are removed from the accumulated buffer up to
     *       {@code overlap} columns.</li>
     *   <li>Leading spaces of the incoming glyph row are skipped up to the
     *       remaining deficit.</li>
     *   <li>The remaining glyph row content is appended.</li>
     * </ol>
     *
     * @param glyph     glyph row data to append
     * @param prevGlyph the immediately preceding glyph (used to compute overlap)
     * @param rows      per-row output buffers
     * @param height    number of font rows
     */
    private static void appendFittedGlyph(char[][] glyph, char[][] prevGlyph,
                                           StringBuilder[] rows, int height) {
        int overlap = fittingOverlap(prevGlyph, glyph);
        for (int r = 0; r < height; r++) {
            char[] row = r < glyph.length ? glyph[r] : new char[0];

            if (overlap > 0 && rows[r].length() >= overlap) {
                int toRemove = removeTrailingSpaces(rows[r], overlap);
                int skip     = skipLeadingSpaces(row, overlap - toRemove);
                rows[r].append(row, skip, row.length - skip);
            } else {
                rows[r].append(row);
            }
        }
    }

    /**
     * Removes up to {@code maxRemove} trailing space characters from {@code sb}.
     *
     * @param sb        the buffer to trim in place
     * @param maxRemove maximum number of spaces to remove
     * @return the actual number of spaces removed
     */
    private static int removeTrailingSpaces(StringBuilder sb, int maxRemove) {
        int end = sb.length();
        int removed = 0;
        while (removed < maxRemove && end - removed - 1 >= 0
               && sb.charAt(end - removed - 1) == ' ') {
            removed++;
        }
        if (removed > 0) {
            sb.delete(end - removed, end);
        }
        return removed;
    }

    /**
     * Returns the number of leading space characters to skip in {@code row},
     * capped at {@code maxSkip}.
     *
     * @param row     glyph row character data
     * @param maxSkip upper bound on spaces to skip
     * @return number of leading spaces to skip; in range {@code [0, maxSkip]}
     */
    private static int skipLeadingSpaces(char[] row, int maxSkip) {
        int skip = 0;
        while (skip < row.length && skip < maxSkip && row[skip] == ' ') {
            skip++;
        }
        return skip;
    }

    /**
     * Trims trailing spaces from each row buffer and appends each row followed
     * by a newline to {@code out}.
     *
     * @param rows per-row output buffers
     * @param out  output buffer to append to
     */
    private static void writeRows(StringBuilder[] rows, StringBuilder out) {
        for (StringBuilder row : rows) {
            out.append(row, 0, trimmedLength(row)).append("\n");
        }
    }

    /**
     * Returns the length of {@code row} with trailing space characters excluded.
     *
     * @param row a row buffer
     * @return index one past the last non-space character; 0 if {@code row} is blank
     */
    private static int trimmedLength(CharSequence row) {
        int end = row.length();
        while (end > 0 && row.charAt(end - 1) == ' ') {
            end--;
        }
        return end;
    }

    /**
     * Computes the fitting overlap between two adjacent glyphs.
     * <p>
     * Fitting moves glyphs together until their visible content is separated
     * by exactly one space column. For each font row the candidate overlap is:
     * <pre>
     *   trailingSpaces(leftRow) + leadingSpaces(rightRow) - 1
     * </pre>
     * The {@code -1} preserves exactly one space of separation. The minimum
     * across all rows is used so that no row's visible content collides.
     *
     * @param left  glyph rows of the left character; must not be {@code null}
     * @param right glyph rows of the right character; must not be {@code null}
     * @return number of columns to collapse; always &gt;= 0
     */
    static int fittingOverlap(char[][] left, char[][] right) {
        int height = Math.min(left.length, right.length);
        int minOverlap = Integer.MAX_VALUE;

        for (int r = 0; r < height; r++) {
            int trailing  = trailingSpaces(left[r]);
            int leading   = leadingSpaces(right[r]);
            int rowOverlap = trailing + leading - 1;
            minOverlap = Math.min(minOverlap, rowOverlap);
        }

        return Math.max(0, minOverlap == Integer.MAX_VALUE ? 0 : minOverlap);
    }

    /**
     * Returns the number of trailing space characters in {@code row}.
     *
     * @param row character data of one glyph row
     * @return trailing space count; 0 if the row is empty or ends with a non-space
     */
    static int trailingSpaces(char[] row) {
        int count = 0;
        for (int i = row.length - 1; i >= 0 && row[i] == ' '; i--) {
            count++;
        }
        return count;
    }

    /**
     * Returns the number of leading space characters in {@code row}.
     *
     * @param row character data of one glyph row
     * @return leading space count; 0 if the row is empty or starts with a non-space
     */
    static int leadingSpaces(char[] row) {
        int count = 0;
        for (int i = 0; i < row.length && row[i] == ' '; i++) {
            count++;
        }
        return count;
    }

    /**
     * Returns the uniform leading whitespace of a glyph: the minimum number of
     * leading spaces across all rows. This value is stripped from the first glyph
     * of every rendered band so that output starts flush at column zero.
     *
     * @param glyph glyph row data; may be {@code null} or empty
     * @return minimum leading spaces across all rows; 0 for a null or empty glyph
     */
    static int leadingSpacesOfGlyph(char[][] glyph) {
        if (glyph == null || glyph.length == 0) {
            return 0;
        }
        int min = Integer.MAX_VALUE;
        for (char[] row : glyph) {
            min = Math.min(min, leadingSpaces(row));
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    /**
     * Returns the width in columns of a glyph, determined by the length of its first row.
     *
     * @param glyph glyph row data; may be {@code null} or empty
     * @return column width; 0 for a null or empty glyph
     */
    static int glyphWidth(char[][] glyph) {
        return (glyph != null && glyph.length > 0) ? glyph[0].length : 0;
    }

    /**
     * Resolves a Unicode code point to its glyph rows.
     * <p>
     * In lenient mode ({@link #isStrict()} {@code == false}), unsupported code
     * points are mapped to the {@code '?'} glyph. In strict mode this method
     * should never be called with an unsupported code point because
     * {@link #validateCodepoints} has already checked them; if it is nonetheless
     * called (e.g. from {@link #renderedWidth} before validation), {@code null}
     * is returned.
     *
     * @param cp Unicode code point
     * @return glyph rows, or {@code null} if unsupported and strict mode is active
     */
    private char[][] resolveGlyph(int cp) {
        if (font.supportsCodepoint(cp)) {
            return font.getCharacter(cp);
        }
        if (!strict) {
            return font.getCharacter('?');
        }
        return null; // strict mode: validated upstream, should not reach here
    }

    /**
     * Appends one blank band (one band per font height row) to {@code out}.
     * Used when an empty input line is encountered.
     *
     * @param out output buffer to append to
     */
    private void appendBlankLine(StringBuilder out) {
        out.append("\n".repeat(Math.max(0, font.getHeight())));
    }

}
