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

    private final String                 name;
    private final char                   hardblank;
    private final int                    height;
    private final int                    baseline;
    private final List<String>           comment;
    private final Map<Integer, char[][]> characters; // codepoint -> rows of chars

    FigletFont(String fontName, char hardblank, int height, int baseline,
               List<String> comment, Map<Integer, char[][]> characters) {

        this.name = requireNonNull(fontName, "fontName");
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

    /** Returns normalized comment lines of this font. */
    public List<String> getComment() {
        return comment;
    }

    /** Returns the character definition for the given Unicode code point, or {@code null} if unsupported. */
    public char[][] getCharacter(int codepoint) {
        char[][] rows = characters.get(codepoint);
        return rows == null ? null : deepCopy(rows);
    }

    /** Returns {@code true} if the font contains a glyph for the given code point. */
    public boolean supportsCodepoint(int codepoint) {
        return characters.containsKey(codepoint);
    }

    /** Returns the unmodifiable map of all defined characters (code point → rows). */
    public Map<Integer, char[][]> getCharacters() {
        Map<Integer, char[][]> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, char[][]> entry : characters.entrySet()) {
            copy.put(entry.getKey(), deepCopy(entry.getValue()));
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
            .add("comment=" + comment.size())
            .add("characters=" + characters.size())
            .toString();
    }

}
