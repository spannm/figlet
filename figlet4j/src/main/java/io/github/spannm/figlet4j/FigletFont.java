package io.github.spannm.figlet4j;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Collections.unmodifiableSortedSet;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;

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

    /** Maximum number of non-ASCII characters listed one by one in {@link #getCharacterSummary()}. */
    private static final int             MAX_LISTED        = 20;
    /** Maximum number of incomplete Unicode blocks counted one by one in {@link #getCharacterSummary()}. */
    private static final int             MAX_LISTED_BLOCKS = 5;
    /** Words in names of Unicode blocks that are kept in uppercase. */
    private static final Set<String>     ACRONYMS          = Set.of("CJK", "IPA", "NKO", "UCAS");

    private final String                 name;
    private final char                   hardblank;
    private final int                    height;
    private final int                    baseline;
    private final List<String>           comment;
    private final Map<Integer, char[][]> characters; // codepoint -> rows of chars
    private final SortedSet<Integer>     supported;  // codepoints with a real glyph
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

        SortedSet<Integer> real = new TreeSet<>();
        copy.forEach((cp, rows) -> {
            if (!isEmpty(rows) && !isPlaceholder(cp, rows)) {
                real.add(cp);
            }
        });
        this.supported = unmodifiableSortedSet(real);
    }

    /**
     * Returns whether a glyph of a non-ASCII character is just that character,
     * written once into a glyph of at least three rows. Fonts use such
     * placeholders for required characters they do not really provide, e.g. a
     * plain {@code ß} in the top row of an otherwise empty glyph. In fonts of one
     * or two rows, the plain character is a legitimate glyph.
     *
     * @param cp   the code point of the glyph
     * @param rows glyph rows
     * @return {@code true} if the glyph is a placeholder
     */
    private boolean isPlaceholder(int cp, char[][] rows) {
        if (cp <= '~' || rows.length < 3) {
            return false;
        }
        int visible = 0;
        for (char[] row : rows) {
            for (char ch : row) {
                if (ch != ' ' && ch != hardblank) {
                    if (ch != cp) {
                        return false;
                    }
                    visible++;
                }
            }
        }
        return visible == 1;
    }

    /**
     * Returns whether a glyph has no columns at all. The FIGfont specification
     * uses such empty glyphs for required characters a font does not provide.
     *
     * @param rows glyph rows
     * @return {@code true} if every row is empty
     */
    private static boolean isEmpty(char[][] rows) {
        for (char[] row : rows) {
            if (row.length > 0) {
                return false;
            }
        }
        return true;
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
     * Returns the character definition for the given Unicode code point, or {@code null} if the font
     * does not define it. Hardblanks are returned as spaces. The result may be an empty glyph, see
     * {@link #supportsCodepoint(int)}.
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

    /**
     * Returns {@code true} if the font can render the given code point.
     * <p>
     * A code point counts as supported only if its glyph is not empty. Fonts
     * that do not provide one of the required characters (ASCII 32-126 and the
     * Deutsch characters) define it with an empty glyph, as the FIGfont
     * specification demands, and such a character would vanish from the output.
     * Some fonts instead write just the character itself into a glyph of several
     * rows; such placeholders for non-ASCII characters do not count either.
     *
     * @param codepoint Unicode code point
     * @return {@code true} if the font defines a non-empty glyph for it
     */
    public boolean supportsCodepoint(int codepoint) {
        return supported.contains(codepoint);
    }

    /**
     * Returns all code points this font can render, in ascending order.
     * Code points defined with an empty glyph are not included
     * (see {@link #supportsCodepoint(int)}).
     *
     * @return unmodifiable sorted set of supported code points
     * @since 1.2.0
     */
    public SortedSet<Integer> getSupportedCodepoints() {
        return supported;
    }

    /**
     * Summarizes the characters this font can render in readable form, e.g.
     * {@code all of ASCII, plus Ä Ö Ü ß ä ö ü} or
     * {@code ASCII (without 0-9 ? @), Latin-1 Supplement, plus 12 of Greek}.
     * <p>
     * Printable ASCII is described as complete, or by the characters it lacks or has,
     * whichever is shorter.
     * Other characters are grouped by Unicode block: complete blocks are named,
     * and the remaining characters are listed if there are at most
     * {@value #MAX_LISTED} of them, or else counted per block, or in total if
     * they spread over more than {@value #MAX_LISTED_BLOCKS} blocks.
     *
     * @return the summary, {@code none} if the font cannot render anything
     * @since 1.2.0
     */
    public String getCharacterSummary() {
        List<String> parts = new ArrayList<>();
        List<Integer> presentAscii = new ArrayList<>();
        List<Integer> missingAscii = new ArrayList<>();
        for (int cp = ' '; cp <= '~'; cp++) {
            (supported.contains(cp) ? presentAscii : missingAscii).add(cp);
        }
        if (missingAscii.isEmpty()) {
            parts.add("all of ASCII");
        } else if (!presentAscii.isEmpty()) {
            String without = String.format("ASCII (without %s)", describeAsciiRuns(missingAscii));
            String only = String.format("ASCII (only %s)", describeAsciiRuns(presentAscii));
            parts.add(without.length() <= only.length() ? without : only);
        }

        Map<Character.UnicodeBlock, List<Integer>> rest = new LinkedHashMap<>();
        for (int cp : supported.tailSet('~' + 1)) {
            if (!Character.isISOControl(cp)) {
                rest.computeIfAbsent(Character.UnicodeBlock.of(cp), b -> new ArrayList<>()).add(cp);
            }
        }
        List<Integer> partial = new ArrayList<>();
        List<String> partialCounts = new ArrayList<>();
        rest.forEach((block, cps) -> {
            if (block != null && cps.size() == printableCount(block, cps.get(0))) {
                parts.add(blockName(block));
            } else {
                partial.addAll(cps);
                partialCounts.add(String.format("%d of %s", cps.size(), block == null ? "unassigned" : blockName(block)));
            }
        });
        if (!partial.isEmpty()) {
            if (partial.size() <= MAX_LISTED) {
                StringJoiner chars = new StringJoiner(" ");
                partial.forEach(cp -> chars.add(displayChar(cp)));
                parts.add(String.format("plus %s", chars));
            } else if (partialCounts.size() <= MAX_LISTED_BLOCKS) {
                parts.add(String.format("plus %s", String.join(", ", partialCounts)));
            } else {
                parts.add(String.format("plus %d more in %d blocks", partial.size(), partialCounts.size()));
            }
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    /**
     * Describes ASCII characters, collapsing runs of at least three consecutive
     * digits or letters into ranges such as {@code 0-9} and {@code a-z}, and
     * naming the space.
     *
     * @param cps ASCII code points in ascending order
     * @return the space-separated description
     */
    private static String describeAsciiRuns(List<Integer> cps) {
        StringJoiner joiner = new StringJoiner(" ");
        int i = 0;
        while (i < cps.size()) {
            int start = cps.get(i);
            int j = i;
            while (j + 1 < cps.size() && cps.get(j + 1) == cps.get(j) + 1 && sameClass(start, cps.get(j + 1))) {
                j++;
            }
            if (Character.isLetterOrDigit(start) && j - i >= 2) {
                joiner.add(String.format("%c-%c", start, cps.get(j)));
                i = j + 1;
            } else {
                joiner.add(start == ' ' ? "space" : String.valueOf((char) start));
                i++;
            }
        }
        return joiner.toString();
    }

    /**
     * Returns whether two ASCII characters are both digits, both uppercase or both lowercase letters.
     *
     * @param a an ASCII code point
     * @param b another ASCII code point
     * @return {@code true} if both belong to the same of these classes
     */
    private static boolean sameClass(int a, int b) {
        return (Character.isDigit(a) && Character.isDigit(b))
            || (Character.isUpperCase(a) && Character.isUpperCase(b))
            || (Character.isLowerCase(a) && Character.isLowerCase(b));
    }

    /**
     * Counts the printable code points of a Unicode block, i.e. the assigned ones other than control characters.
     *
     * @param block the block
     * @param anyCp any code point inside the block, from where its bounds are searched
     * @return the number of printable code points
     */
    private static int printableCount(Character.UnicodeBlock block, int anyCp) {
        int first = anyCp;
        while (first > 0 && block.equals(Character.UnicodeBlock.of(first - 1))) {
            first--;
        }
        int count = 0;
        for (int cp = first; block.equals(Character.UnicodeBlock.of(cp)); cp++) {
            if (Character.isDefined(cp) && !Character.isISOControl(cp)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Turns the constant name of a Unicode block into a readable name close to
     * the official one, e.g. {@code Latin-1 Supplement} or {@code CJK Symbols and Punctuation}.
     *
     * @param block the block
     * @return the readable name
     */
    private static String blockName(Character.UnicodeBlock block) {
        StringJoiner joiner = new StringJoiner(" ");
        for (String word : block.toString().split("_", -1)) {
            if (ACRONYMS.contains(word)) {
                joiner.add(word);
            } else if ("AND".equals(word)) {
                joiner.add("and");
            } else {
                joiner.add(word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return joiner.toString().replaceAll("(Latin) (1)\\b|(Extended|Forms) ([A-Z])\\b", "$1$3-$2$4");
    }

    /**
     * Returns a code point as displayable text: the character itself, or its
     * Unicode notation if it is invisible, such as a space or a control character.
     *
     * @param cp the code point
     * @return the displayable text
     */
    private static String displayChar(int cp) {
        int type = Character.getType(cp);
        if (Character.isSpaceChar(cp) || Character.isISOControl(cp) || type == Character.FORMAT) {
            return String.format("U+%04X", cp);
        }
        return new String(Character.toChars(cp));
    }

    /**
     * Returns the code points of {@code text} this font cannot render, in order
     * of their first occurrence and without duplicates. Line breaks are ignored,
     * as they are not rendered as glyphs.
     *
     * @param text the text to check; must not be {@code null}
     * @return unmodifiable set of unsupported code points, empty if the font can render all of {@code text}
     * @since 1.2.0
     */
    public Set<Integer> findUnsupportedCodepoints(CharSequence text) {
        requireNonNull(text, "text");
        Set<Integer> unsupported = new LinkedHashSet<>();
        text.codePoints()
            .filter(cp -> cp != '\n' && cp != '\r' && !supportsCodepoint(cp))
            .forEach(unsupported::add);
        return unmodifiableSet(unsupported);
    }

    /**
     * Returns the unmodifiable map of all defined characters (code point → rows), with hardblanks as spaces.
     * Unlike {@link #getSupportedCodepoints()}, it includes characters defined with an empty glyph.
     */
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
            .add("supported=" + supported.size())
            .toString();
    }

}
