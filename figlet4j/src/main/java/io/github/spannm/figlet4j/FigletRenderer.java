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
 * Adjacent glyphs are moved together until they touch, as by the original
 * FIGlet: the blank columns between the visible characters of two glyphs are
 * removed. The first glyph of a line is stripped of its uniform leading blanks.
 * Hardblanks (see {@link FigletFont#getHardblank()}) count as visible while
 * fitting and are printed as spaces, which is what keeps the space character
 * of most fonts from being fitted away. Fonts whose header asks for full width
 * (see {@link FigletFont#isFullWidth()}) are not fitted at all: their glyphs
 * are placed next to each other unchanged.
 *
 * <h2>Smushing</h2>
 * Fonts whose header asks for smushing (see {@link FigletFont#isSmushing()})
 * are laid out as by the original FIGlet: adjacent glyphs are moved together
 * until they touch, and one column further where the two touching characters
 * can be merged into one according to the smushing rules of the font.
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

    /** Character classes of the hierarchy smushing rule, lowest first. */
    private static final String[] HIERARCHY = {"|", "/\\", "[]", "{}", "()", "<>"};

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
            throw new IllegalArgumentException(String.format("width must be >= 1, got: %d", width));
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
        return new Band(font.getHeight(), font.isRightToLeft(), font.layout(), font.getHardblank());
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
     * Smushes two horizontally adjacent characters according to the smushing
     * rules in {@code layout}, as defined by the FIGfont specification. A blank
     * yields the other character. Without any rule bit, universal smushing
     * applies: the later character wins, except over a hardblank.
     *
     * @param left        the left character
     * @param right       the right character
     * @param layout      the font's layout bits, see {@link FigletFont#layout()}
     * @param hardblank   the font's hardblank
     * @param rightToLeft whether the left character is the later one
     * @return the merged character, or 0 if the two cannot be smushed
     */
    static char smush(char left, char right, int layout, char hardblank, boolean rightToLeft) {
        if (left == ' ') {
            return right;
        } else if (right == ' ') {
            return left;
        } else if ((layout & FigletFont.LAYOUT_SMUSHING) == 0) {
            return 0;
        } else if ((layout & 63) == 0) {
            if (left == hardblank) {
                return right;
            } else if (right == hardblank) {
                return left;
            }
            return rightToLeft ? left : right;
        } else if (left == hardblank || right == hardblank) {
            return (layout & FigletFont.SMUSH_HARDBLANK) != 0 && left == right ? left : 0;
        }
        return smushByRules(left, right, layout);
    }

    /**
     * Applies the controlled smushing rules for two visible characters, neither a hardblank.
     *
     * @param left   the left character
     * @param right  the right character
     * @param layout the font's layout bits
     * @return the merged character, or 0 if no enabled rule applies
     */
    private static char smushByRules(char left, char right, int layout) {
        if ((layout & FigletFont.SMUSH_EQUAL) != 0 && left == right) {
            return left;
        }
        if ((layout & FigletFont.SMUSH_LOWLINE) != 0) {
            if (left == '_' && "|/\\[]{}()<>".indexOf(right) >= 0) {
                return right;
            } else if (right == '_' && "|/\\[]{}()<>".indexOf(left) >= 0) {
                return left;
            }
        }
        if ((layout & FigletFont.SMUSH_HIERARCHY) != 0) {
            char winner = hierarchyWinner(left, right);
            if (winner != 0) {
                return winner;
            }
        }
        if ((layout & FigletFont.SMUSH_PAIR) != 0 && isOpposingPair(left, right)) {
            return '|';
        }
        if ((layout & FigletFont.SMUSH_BIGX) != 0) {
            if (left == '/' && right == '\\') {
                return '|';
            } else if (left == '\\' && right == '/') {
                return 'Y';
            } else if (left == '>' && right == '<') {
                return 'X';
            }
        }
        return 0;
    }

    /**
     * Returns the character of the higher class if {@code left} and {@code right}
     * belong to different classes of the hierarchy rule.
     *
     * @param left  the left character
     * @param right the right character
     * @return the winning character, or 0 if the rule does not apply
     */
    private static char hierarchyWinner(char left, char right) {
        int leftClass  = hierarchyClass(left);
        int rightClass = hierarchyClass(right);
        if (leftClass < 0 || rightClass < 0 || leftClass == rightClass) {
            return 0;
        }
        return leftClass > rightClass ? left : right;
    }

    /**
     * Returns the class index of {@code ch} in the hierarchy rule.
     *
     * @param ch a character
     * @return the class index, or -1 if {@code ch} belongs to no class
     */
    private static int hierarchyClass(char ch) {
        for (int i = 0; i < HIERARCHY.length; i++) {
            if (HIERARCHY[i].indexOf(ch) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns whether {@code left} and {@code right} are opposing brackets,
     * braces or parentheses, in either order.
     *
     * @param left  the left character
     * @param right the right character
     * @return {@code true} for an opposing pair
     */
    private static boolean isOpposingPair(char left, char right) {
        String pair = new String(new char[] {left, right});
        return "[]".equals(pair) || "][".equals(pair) || "{}".equals(pair) || "}{".equals(pair)
            || "()".equals(pair) || ")(".equals(pair);
    }

    /**
     * Appends {@code glyph} to {@code rows} without fitting, padding shorter
     * glyph rows so that all rows stay aligned.
     *
     * @param glyph glyph row data
     * @param rows  per-row output buffers
     */
    private static void appendUnfittedGlyph(char[][] glyph, StringBuilder[] rows) {
        int width = 0;
        for (char[] row : glyph) {
            width = Math.max(width, row.length);
        }
        for (int r = 0; r < rows.length; r++) {
            char[] row = r < glyph.length ? glyph[r] : new char[0];
            rows[r].append(row).append(" ".repeat(width - row.length));
        }
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
     * One horizontal band of fitted glyphs under construction, held as per-row
     * buffers against which the next glyph is fitted.
     * <p>
     * A mirrored band is used for right-to-left fonts: it is built from mirrored
     * glyphs with the regular left-to-right fitting, and mirrored back in
     * {@link #lines()}. Since fitting is symmetric, this yields the glyphs in
     * right-to-left order.
     */
    private static final class Band {

        private final StringBuilder[] rows;
        private final boolean         mirrored;
        private final int             layout;
        private final char            hardblank;
        private int                   prevWidth;

        /**
         * Creates an empty band.
         *
         * @param height    number of font rows
         * @param mirrored  whether the band is built from mirrored glyphs
         * @param layout    the font's layout bits, see {@link FigletFont#layout()}
         * @param hardblank the font's hardblank, printed as a space
         */
        Band(int height, boolean mirrored, int layout, char hardblank) {
            this.mirrored = mirrored;
            this.layout = layout;
            this.hardblank = hardblank;
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
            layout = other.layout;
            hardblank = other.hardblank;
            prevWidth = other.prevWidth;
        }

        /**
         * Appends {@code glyph}, fitted or smushed against the content of this
         * band. At full width, the glyph is appended unchanged instead. Empty
         * glyphs are skipped.
         *
         * @param glyph glyph row data
         */
        void append(char[][] glyph) {
            if (glyph.length == 0) {
                return;
            }
            if ((layout & (FigletFont.LAYOUT_FITTING | FigletFont.LAYOUT_SMUSHING)) == 0) {
                appendUnfittedGlyph(glyph, rows);
            } else {
                appendOverlappingGlyph(glyph);
            }
            prevWidth = glyph[0].length;
        }

        /**
         * Appends {@code glyph} overlapping the content of this band: the
         * overlapping columns are merged character by character, the rest of
         * the glyph is appended. For the first glyph of a band, the overlap
         * strips its uniform leading blanks.
         *
         * @param glyph glyph row data
         */
        private void appendOverlappingGlyph(char[][] glyph) {
            int amount = overlapAmount(glyph);
            int width  = glyph[0].length;
            for (int r = 0; r < rows.length; r++) {
                StringBuilder row = rows[r];
                char[] glyphRow = r < glyph.length ? glyph[r] : new char[0];
                int keep = row.length() - amount;
                String tail = row.substring(Math.max(0, keep));
                row.setLength(Math.max(0, keep));
                for (int k = 0; k < glyphRow.length; k++) {
                    int pos = keep + k;
                    if (pos < 0) {
                        continue;
                    }
                    if (k < amount) {
                        char merged = smushPair(tail.charAt(pos - Math.max(0, keep)), glyphRow[k], width);
                        row.append(merged == 0 ? glyphRow[k] : merged);
                    } else {
                        row.append(glyphRow[k]);
                    }
                }
            }
        }

        /**
         * Computes by how many columns {@code glyph} is moved into the content
         * of this band: per row, until its first visible character touches the
         * last visible character of the row, plus one if the two can be smushed
         * (which, without smushing, only a blank can).
         * The minimum across all rows is used, but never more than the width of
         * the glyph.
         *
         * @param glyph glyph row data
         * @return number of overlapping columns
         */
        private int overlapAmount(char[][] glyph) {
            int width  = glyph[0].length;
            int amount = width;
            for (int r = 0; r < rows.length; r++) {
                StringBuilder row = rows[r];
                char[] glyphRow = r < glyph.length ? glyph[r] : new char[0];

                int lineBound = row.length() - 1;
                while (lineBound > 0 && row.charAt(lineBound) == ' ') {
                    lineBound--;
                }
                char left = row.length() == 0 ? 0 : row.charAt(Math.max(0, lineBound));

                int charBound = leadingSpaces(glyphRow);
                char right = charBound < glyphRow.length ? glyphRow[charBound] : 0;

                int rowAmount = charBound + row.length() - 1 - Math.max(0, lineBound);
                if (left == 0 || left == ' ' || (right != 0 && smushPair(left, right, width) != 0)) {
                    rowAmount++;
                }
                amount = Math.min(amount, rowAmount);
            }
            return amount;
        }

        /**
         * Smushes a character of this band with a character of the glyph being
         * appended, taking the print direction into account: in a mirrored band,
         * the glyph is on the left.
         *
         * @param bandChar   character of this band
         * @param glyphChar  character of the glyph being appended
         * @param glyphWidth width of the glyph being appended
         * @return the merged character, or 0 if the two cannot be smushed
         */
        private char smushPair(char bandChar, char glyphChar, int glyphWidth) {
            if (bandChar != ' ' && glyphChar != ' ' && (prevWidth < 2 || glyphWidth < 2)) {
                return 0;
            }
            return mirrored
                ? smush(glyphChar, bandChar, layout, hardblank, true)
                : smush(bandChar, glyphChar, layout, hardblank, false);
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
         * Returns the output lines of this band, with hardblanks printed as
         * spaces and without trailing spaces.
         *
         * @return one line per font row
         */
        List<String> lines() {
            List<String> lines = mirrored ? mirroredBack() : new ArrayList<>(rows.length);
            if (!mirrored) {
                for (StringBuilder row : rows) {
                    lines.add(row.toString());
                }
            }
            for (int r = 0; r < lines.size(); r++) {
                String printed = lines.get(r).replace(hardblank, ' ');
                lines.set(r, printed.substring(0, trimmedLength(printed)));
            }
            return lines;
        }

        /**
         * Returns the rows of this mirrored band padded, mirrored back and
         * stripped of their uniform leading whitespace, so that they start
         * flush at column zero.
         *
         * @return one row per font row, hardblanks not yet replaced
         */
        private List<String> mirroredBack() {
            List<String> lines = new ArrayList<>(rows.length);
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
                lines.set(r, lines.get(r).substring(strip));
            }
            return lines;
        }

    }

}
