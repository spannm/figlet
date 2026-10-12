package io.github.spannm.figlet4j;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Represents a parsed FIGfont, holding all character definitions and header metadata.
 * <p>
 * A FIGfont is loaded from a {@code .flf} file.<br>
 * Format of the header line:
 * <pre>
 *   flf2a$ 6 5 20 15 3 0 24463 229
 *         | | | |  |  | |     |
 *         | | | |  |  | |     +-- codetag_count
 *         | | | |  |  | +------- full_layout
 *         | | | |  |  +--------- print_direction
 *         | | | |  +------------ old_layout
 *         | | | +--------------- comment_lines
 *         | | +----------------- baseline
 *         | +------------------- height
 *         +--------------------- hardblank character
 * </pre>
 *
 * @see <a href="http://www.jave.de/figlet/figfont.html">FIGfont specification</a>
 *
 * @author Markus Spann
 * @since 1.0.0
 */
public final class FigletFont {

    /** Layout bit: smush two equal characters into one. */
    static final int                     SMUSH_EQUAL     = 1;
    /** Layout bit: an underscore is replaced by a border character. */
    static final int                     SMUSH_LOWLINE   = 2;
    /** Layout bit: of two border characters of different classes, the later class wins. */
    static final int                     SMUSH_HIERARCHY = 4;
    /** Layout bit: opposing brackets, braces or parentheses become a vertical bar. */
    static final int                     SMUSH_PAIR      = 8;
    /** Layout bit: slashes and angle brackets form a vertical bar, a Y or an X. */
    static final int                     SMUSH_BIGX      = 16;
    /** Layout bit: two hardblanks are smushed into one. */
    static final int                     SMUSH_HARDBLANK = 32;
    /** Layout bit: glyphs are fitted together. */
    static final int                     LAYOUT_FITTING  = 64;
    /** Layout bit: glyphs are smushed together. */
    static final int                     LAYOUT_SMUSHING = 128;

    private final String                 name;
    private final char                   hardblank;
    private final int                    height;
    private final int                    baseline;
    private final List<String>           comment;
    private final Map<Integer, char[][]> characters; // codepoint -> rows of chars
    private final boolean                rightToLeft;
    private final int                    layout;

    FigletFont(String fontName, char hardblank, int height, int baseline,
               List<String> comment, Map<Integer, char[][]> characters) {
        this(fontName, hardblank, height, baseline, comment, characters, false, LAYOUT_FITTING);
    }

    FigletFont(String fontName, char hardblank, int height, int baseline,
               List<String> comment, Map<Integer, char[][]> characters, boolean rightToLeft, int layout) {

        this.name = requireNonNull(fontName, "fontName");
        this.rightToLeft = rightToLeft;
        this.layout = layout;
        this.hardblank = hardblank;
        this.height = height;
        this.baseline = baseline;
        this.comment = unmodifiableList(requireNonNull(comment, "comment"));

        // deep-copy every glyph matrix so this font is immune to the caller mutating
        // either the array it passed in, or (via getCharacter/getCharacters) an array
        // this font has already handed out - both must be defended against now that
        // FigletFontRegistry caches and shares FigletFont instances across renders
        Map<Integer, char[][]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, char[][]> entry : requireNonNull(characters, "characters").entrySet()) {
            copy.put(entry.getKey(), deepCopy(entry.getValue()));
        }
        this.characters = unmodifiableMap(copy);
    }

    private static char[][] deepCopy(char[][] rows) {
        char[][] copy = new char[rows.length][];
        for (int i = 0; i < rows.length; i++) {
            copy[i] = rows[i].clone();
        }
        return copy;
    }

    /** Returns the font name (derived from the file name, without extension). */
    public String getName() {
        return name;
    }

    /** The hardblank character used inside the font to represent a visible space. */
    public char getHardblank() {
        return hardblank;
    }

    /** Height (in text rows) of every character in this font. */
    public int getHeight() {
        return height;
    }

    /** Baseline row (0-based) within the character height. */
    public int getBaseline() {
        return baseline;
    }

    /**
     * Returns whether this font is printed right-to-left, as specified by the
     * optional {@code print_direction} header field.
     *
     * @return {@code true} if {@code print_direction} is 1, {@code false} for left-to-right
     * @since 1.1.0
     */
    public boolean isRightToLeft() {
        return rightToLeft;
    }

    /**
     * Returns whether this font is rendered at full width, i.e. without fitting
     * glyphs together, as specified by the {@code full_layout} header field or,
     * if that is absent, by {@code old_layout} being -1.
     *
     * @return {@code true} for full width, {@code false} if glyphs are fitted or smushed
     * @since 1.1.0
     */
    public boolean isFullWidth() {
        return (layout & (LAYOUT_FITTING | LAYOUT_SMUSHING)) == 0;
    }

    /**
     * Returns whether glyphs of this font are smushed together, i.e. moved one
     * column closer than fitting and merged where their border characters
     * touch, as specified by the {@code full_layout} header field or, if that
     * is absent, by a positive {@code old_layout}.
     *
     * @return {@code true} if glyphs are smushed
     * @since 1.1.0
     */
    public boolean isSmushing() {
        return (layout & LAYOUT_SMUSHING) != 0;
    }

    /**
     * Returns the horizontal layout bits, as defined for the {@code full_layout}
     * header field: the smushing rules ({@code SMUSH_*}) plus
     * {@link #LAYOUT_FITTING} and {@link #LAYOUT_SMUSHING}.
     *
     * @return the layout bits
     */
    int layout() {
        return layout;
    }

    /** Returns normalized comment lines of this font. */
    public List<String> getComment() {
        return comment;
    }

    /**
     * Returns the character definition for the given Unicode code point, or {@code null} if unsupported.
     * Hardblanks are returned as spaces.
     */
    public char[][] getCharacter(int codepoint) {
        char[][] rows = characters.get(codepoint);
        return rows == null ? null : withoutHardblanks(rows);
    }

    /**
     * Returns a copy of {@code rows} with every hardblank replaced by a space.
     *
     * @param rows glyph rows as stored in this font
     * @return the copied glyph rows
     */
    private char[][] withoutHardblanks(char[][] rows) {
        char[][] copy = deepCopy(rows);
        for (char[] row : copy) {
            for (int i = 0; i < row.length; i++) {
                if (row[i] == hardblank) {
                    row[i] = ' ';
                }
            }
        }
        return copy;
    }

    /**
     * Returns the internal glyph for the given code point without copying it,
     * for read-only use by the renderer. Unlike {@link #getCharacter(int)}, the
     * glyph still contains the font's hardblanks.
     *
     * @param codepoint Unicode code point
     * @return the glyph rows, or an empty glyph if unsupported; must not be modified
     */
    char[][] glyph(int codepoint) {
        return characters.getOrDefault(codepoint, new char[0][]);
    }

    /** Returns {@code true} if the font contains a glyph for the given code point. */
    public boolean supportsCodepoint(int codepoint) {
        return characters.containsKey(codepoint);
    }

    /** Returns the unmodifiable map of all defined characters (code point → rows), with hardblanks as spaces. */
    public Map<Integer, char[][]> getCharacters() {
        Map<Integer, char[][]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, char[][]> entry : characters.entrySet()) {
            copy.put(entry.getKey(), withoutHardblanks(entry.getValue()));
        }
        return unmodifiableMap(copy);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
            .add("name='" + name + "'")
            .add("hardblank='" + hardblank + "'")
            .add("height=" + height)
            .add("baseline=" + baseline)
            .add("rightToLeft=" + rightToLeft)
            .add("layout=" + layout)
            .add("comment=" + comment.size())
            .add("characters=" + characters.size())
            .toString();
    }

}
