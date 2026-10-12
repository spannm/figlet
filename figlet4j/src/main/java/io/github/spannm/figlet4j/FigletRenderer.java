package io.github.spannm.figlet4j;

import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <h2>Print direction</h2>
 * Fonts whose header specifies {@code print_direction} 1 (see
 * {@link FigletFont#isRightToLeft()}) are rendered right-to-left: the first
 * character of each line appears rightmost. Word wrapping still fills lines
 * in reading order. The output is not right-justified.
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
                    cp < 32 ? String.format("\\u%04X", cp) : new String(Character.toChars(cp)),
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
     * The wrap decision is made by actually appending the token to a copy of
     * the current band and measuring its real width, rather than by summing
     * a per-glyph width estimate: the fitting overlap between two glyphs can
     * exceed either glyph's own width (e.g. a wide space glyph following a
     * glyph with few trailing spaces), a case a simple additive estimate
     * cannot represent. Measuring the real, tentative band keeps the wrap
     * decision and the final output in agreement by construction. Since
     * fitting only depends on the band built so far and its last glyph,
     * extending a copy yields the same result as rebuilding the band from
     * scratch, without the quadratic cost.
     *
     * @param words word and space tokens for one logical input line
     * @param out   output buffer to append rendered rows to
     */
    private void renderWords(List<String> words, StringBuilder out) {
        if (words.isEmpty()) {
            appendBlankLine(out);
            return;
        }

        Band band = null;

        for (String token : words) {
            Band candidate = band == null ? newBand() : new Band(band);
            appendToken(candidate, token);

            if (band != null && candidate.width() > width) {
                writeRows(band, out);
                band = null;
                if (" ".equals(token)) {
                    continue; // discard leading space on wrapped line
                }
                candidate = newBand();
                appendToken(candidate, token);
            }
            band = candidate;
        }
        if (band != null) {
            writeRows(band, out);
        }
    }

    /**
     * Creates an empty band for this renderer's font, mirrored for right-to-left fonts.
     *
     * @return a new, empty band
     */
    private Band newBand() {
        return new Band(font.getHeight(), font.isRightToLeft());
    }

    /**
     * Appends the glyphs of all code points of {@code token} to {@code band}.
     *
     * @param band  the band to extend
     * @param token a word or space token
     */
    private void appendToken(Band band, String token) {
        token.codePoints().forEach(cp -> {
            char[][] glyph = resolveGlyph(cp);
            band.append(band.mirrored ? mirror(glyph) : glyph);
        });
    }

    /**
     * Returns a horizontally mirrored copy of {@code glyph}, with shorter rows
     * padded so that all rows stay aligned.
     *
     * @param glyph glyph row data
     * @return the mirrored glyph
     */
    static char[][] mirror(char[][] glyph) {
        int width = 0;
        for (char[] row : glyph) {
            width = Math.max(width, row.length);
        }
        char[][] mirrored = new char[glyph.length][width];
        for (int r = 0; r < glyph.length; r++) {
            Arrays.fill(mirrored[r], ' ');
            for (int c = 0; c < glyph[r].length; c++) {
                mirrored[r][width - 1 - c] = glyph[r][c];
            }
        }
        return mirrored;
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
     * Appends each output line of {@code band} followed by a newline to {@code out}.
     *
     * @param band the finished band
     * @param out  output buffer to append to
     */
    private static void writeRows(Band band, StringBuilder out) {
        for (String line : band.lines()) {
            out.append(line).append('\n');
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
     * called, an empty glyph is returned, which the caller skips.
     * <p>
     * The returned array is the font's internal glyph and must not be modified.
     *
     * @param cp Unicode code point
     * @return glyph rows, or an empty glyph if unsupported and strict mode is active
     *         (or if the font lacks a {@code '?'} glyph in lenient mode)
     */
    private char[][] resolveGlyph(int cp) {
        if (font.supportsCodepoint(cp)) {
            return font.glyph(cp);
        }
        if (!strict) {
            return font.glyph('?');
        }
        return new char[0][]; // strict mode: validated upstream, should not reach here
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

    /**
     * One horizontal band of fitted glyphs under construction: the per-row
     * buffers plus the last glyph appended, against which the next glyph is fitted.
     * <p>
     * A mirrored band is used for right-to-left fonts: it is built from mirrored
     * glyphs with the regular left-to-right fitting, and mirrored back in
     * {@link #lines()}. Since fitting is symmetric, this yields the glyphs in
     * right-to-left order.
     */
    private static final class Band {

        private final StringBuilder[] rows;
        private final boolean         mirrored;
        private char[][]              prevGlyph;

        /**
         * Creates an empty band.
         *
         * @param height   number of font rows
         * @param mirrored whether the band is built from mirrored glyphs
         */
        Band(int height, boolean mirrored) {
            this.mirrored = mirrored;
            rows = new StringBuilder[height];
            for (int r = 0; r < height; r++) {
                rows[r] = new StringBuilder();
            }
        }

        /**
         * Creates a copy of {@code other} that can be extended independently.
         *
         * @param other the band to copy
         */
        Band(Band other) {
            rows = new StringBuilder[other.rows.length];
            for (int r = 0; r < rows.length; r++) {
                rows[r] = new StringBuilder(other.rows[r]);
            }
            mirrored = other.mirrored;
            prevGlyph = other.prevGlyph;
        }

        /**
         * Appends {@code glyph}, fitted against the previously appended glyph.
         * The first glyph of a band is stripped of its uniform leading whitespace
         * so that the output starts flush at column zero. Empty glyphs are skipped.
         *
         * @param glyph glyph row data
         */
        void append(char[][] glyph) {
            if (glyph.length == 0) {
                return;
            }
            if (prevGlyph == null) {
                appendFirstGlyph(glyph, rows, rows.length);
            } else {
                appendFittedGlyph(glyph, prevGlyph, rows, rows.length);
            }
            prevGlyph = glyph;
        }

        /**
         * Returns the real rendered width of this band, i.e. the length of its
         * longest output line.
         *
         * @return widest output line in columns; 0 for a band with no visible content
         */
        int width() {
            int max = 0;
            for (String line : lines()) {
                max = Math.max(max, line.length());
            }
            return max;
        }

        /**
         * Returns the output lines of this band, without trailing spaces. A
         * mirrored band is padded, mirrored back and stripped of its uniform
         * leading whitespace, so that it starts flush at column zero.
         *
         * @return one line per font row
         */
        List<String> lines() {
            List<String> lines = new ArrayList<>(rows.length);
            if (!mirrored) {
                for (StringBuilder row : rows) {
                    lines.add(row.substring(0, trimmedLength(row)));
                }
                return lines;
            }

            int width = 0;
            for (StringBuilder row : rows) {
                width = Math.max(width, row.length());
            }
            int strip = width;
            for (StringBuilder row : rows) {
                StringBuilder line = new StringBuilder(row);
                line.append(" ".repeat(width - row.length())).reverse();
                strip = Math.min(strip, leadingSpaces(line.toString().toCharArray()));
                lines.add(line.toString());
            }
            for (int r = 0; r < lines.size(); r++) {
                String line = lines.get(r);
                lines.set(r, line.substring(Math.min(strip, trimmedLength(line)), trimmedLength(line)));
            }
            return lines;
        }

    }

}
