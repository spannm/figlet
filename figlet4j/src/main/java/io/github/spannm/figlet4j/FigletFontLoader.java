package io.github.spannm.figlet4j;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses FIGfont ({@code .flf}) and TOIlet font ({@code .tlf}) files into {@link FigletFont} instances.
 * <p>
 * Loading order for named fonts
 * <ol>
 *   <li>Classpath resource at {@code io/github/spannm/figlet4j/fonts/<name>.<extension>}</li>
 *   <li>File-system path passed explicitly</li>
 * </ol>
 * <p>
 * Format (brief)
 * <pre>
 * flf2a{hardblank} {height} {baseline} {max_length} {old_layout} {comment_lines}
 * or
 * tlf2a{hardblank} {height} {baseline} {max_length} {old_layout} {comment_lines}
 * ... comment lines ...
 * {character rows for ASCII 32..126, then 196,214,220,228,246,252,223}
 * {optional tagged Unicode characters}
 * </pre>
 * Every row of a character ends with an end mark, usually {@code @}, doubled on
 * the last row of the character. As in the original FIGlet, the end mark is
 * taken from each row itself: trailing whitespace is ignored, and every trailing
 * repetition of the last character is stripped.
 *
 * @since 1.0.0
 */
public final class FigletFontLoader {

    private static final Logger LOGGER    = System.getLogger(FigletFontLoader.class.getName());

    /** Required magic signature at the start of every {@code .flf} file. */
    public static final String  MAGIC_FLF = "flf2a";

    /** Required magic signature at the start of every {@code .tlf} file. */
    public static final String  MAGIC_TLF = "tlf2a";

    /**
     * Standard ASCII code points that every FIGfont must define (32–126),
     * plus the mandatory Deutsch characters (196=Ä, 214=Ö, 220=Ü, 228=ä, 246=ö, 252=ü, 223=ß).
     */
    private static final int[]  REQUIRED_CODEPOINTS;

    static {
        int[] cp = new int[95 + 7];
        for (int i = 0; i < 95; i++) {
            cp[i] = 32 + i;
        }
        int[] deutsch = {196, 214, 220, 228, 246, 252, 223};
        System.arraycopy(deutsch, 0, cp, 95, deutsch.length);
        REQUIRED_CODEPOINTS = cp;
    }

    private FigletFontLoader() {
        throw new UnsupportedOperationException(
            String.format("Utility class %s cannot be instantiated", getClass().getSimpleName()));
    }

    /**
     * Loads a bundled font by name (without extension).
     *
     * @param fontName case-insensitive font name, e.g. {@code "standard"} or {@code "mono9"}
     * @return parsed {@link FigletFont}
     * @throws FigletException if the font is not found on the classpath or cannot be parsed
     */
    public static FigletFont loadBuiltin(String fontName) {
        requireNonNull(fontName, "fontName");
        String resourcePath = FigletFontRegistry.resolveBuiltin(fontName);
        if (resourcePath != null) {
            return loadBuiltin(fontName, resourcePath);
        }
        throw new FigletException(String.format("Bundled font not found on classpath: %s", fontName));
    }

    /**
     * Loads a bundled font by name using an explicit classpath resource path.
     *
     * @param fontName     font name used for instantiation
     * @param resourcePath explicit path on the classpath, e.g. {@code "fonts/standard.flf"}
     * @return parsed {@link FigletFont}
     * @throws FigletException if the resource cannot be found or parsed
     */
    public static FigletFont loadBuiltin(String fontName, String resourcePath) {
        requireNonNull(fontName, "fontName");
        requireNonNull(resourcePath, "resourcePath");

        URL url = FigletFontLoader.class.getClassLoader().getResource(resourcePath);
        if (url != null) {
            LOGGER.log(Level.DEBUG, "Loading bundled font {0} from {1}", fontName, resourcePath);
            try (InputStream is = url.openStream()) {
                return parse(fontName, is);
            } catch (IOException ex) {
                throw new FigletException(String.format("Failed to read bundled font: %s", resourcePath), ex);
            }
        }

        throw new FigletException(String.format("Bundled font resource not found on classpath: %s", resourcePath));
    }

    /**
     * Loads a font from an external {@code .flf} or {@code .tlf} file on the file system.
     *
     * @param path path to the font file
     * @return parsed {@link FigletFont}
     * @throws FigletException if the file does not exist, cannot be read or parsed
     */
    public static FigletFont loadFromFile(Path path) {
        requireNonNull(path, "path");
        if (!Files.exists(path)) {
            throw new FigletException(String.format("Font file not found: %s", path));
        } else if (!Files.isReadable(path)) {
            throw new FigletException(String.format("Font file not readable: %s", path));
        }

        String fileName = path.getFileName().toString();
        String name = FigletFontType.removeExtension(fileName);

        LOGGER.log(Level.DEBUG, "Loading font from file: {0}", path);
        try (InputStream is = Files.newInputStream(path)) {
            return parse(name, is);
        } catch (IOException ex) {
            throw new FigletException(String.format("Failed to read font file: %s", path), ex);
        }
    }

    /**
     * Loads a font from an arbitrary {@link InputStream}.
     * The caller is responsible for closing the stream.
     *
     * @param fontName logical font name (used for display / error messages)
     * @param is       input stream of the font content
     * @return parsed {@link FigletFont}
     * @throws FigletException if the stream cannot be parsed as a valid font
     */
    public static FigletFont loadFromStream(String fontName, InputStream is) {
        requireNonNull(fontName, "fontName");
        requireNonNull(is, "is");
        return parse(fontName, is);
    }

    @SuppressWarnings("StringSplitter")
    private static FigletFont parse(String fontName, InputStream is) {
        List<String> lines = readLines(is);
        if (lines.isEmpty()) {
            throw new FigletException(String.format("Font file is empty: %s", fontName));
        }

        // header
        String header = lines.get(0);
        String magic;
        if (header.startsWith(MAGIC_FLF)) {
            magic = MAGIC_FLF;
        } else if (header.startsWith(MAGIC_TLF)) {
            magic = MAGIC_TLF;
        } else {
            throw new FigletException(String.format("Not a valid FIGfont or TOIlet font (missing signature): %s", fontName));
        }

        if (header.length() <= magic.length()) {
            throw new FigletException(String.format("Malformed font header (missing hardblank character) in font: %s", fontName));
        }
        char hardblank = header.charAt(magic.length());
        String[] parts = header.substring(magic.length() + 1).trim().split("\\s+");
        if (parts.length < 5) {
            throw new FigletException(String.format("Malformed font header in font: %s", fontName));
        }

        int height;
        int baseline;
        int commentLines;
        try {
            height = Integer.parseInt(parts[0]);
            baseline = Integer.parseInt(parts[1]);
            commentLines = Integer.parseInt(parts[4]);
        } catch (NumberFormatException ex) {
            throw new FigletException(String.format("Malformed font header (non-numeric field) in font: %s", fontName), ex);
        }

        // print_direction is optional: 0 = left-to-right, 1 = right-to-left
        boolean rightToLeft = parts.length > 5 && "1".equals(parts[5]);
        int layout = layout(parts);

        if (height <= 0) {
            throw new FigletException(String.format(
                "Malformed font header (height must be a positive number, was %d) in font: %s", height, fontName));
        }
        if (commentLines < 0) {
            throw new FigletException(String.format(
                "Malformed font header (comment_lines must not be negative, was %d) in font: %s", commentLines, fontName));
        }

        // comment block
        int dataStart = 1 + commentLines;
        if (dataStart > lines.size()) {
            throw new FigletException(String.format("Font file too short to contain comment block: %s", fontName));
        }

        List<String> rawComment = lines.subList(1, dataStart);
        List<String> comment = FigletFontCommentNormalizer.normalizeComment(fontName, rawComment);

        // character data
        Map<Integer, char[][]> characters = new LinkedHashMap<>();
        int pos = dataStart;

        // required characters in fixed order: ASCII 32-126, then Deutsch extras
        for (int cp : REQUIRED_CODEPOINTS) {
            if (pos >= lines.size()) {
                break; // truncated font - be lenient, stop early
            }
            char[][] glyph = readGlyph(lines, pos, height, fontName, cp);
            characters.put(cp, glyph);
            pos += height;
        }

        // optional tagged Unicode characters: lines of the form "NNN  comment"
        while (pos < lines.size()) {
            String tag = lines.get(pos).trim();
            if (tag.isEmpty()) {
                pos++;
                continue;
            }
            int cp = parseCodepoint(tag);
            if (cp < 0) {
                pos++;
                continue;
            }
            pos++;
            if (pos + height > lines.size()) {
                break;
            }
            char[][] glyph = readGlyph(lines, pos, height, fontName, cp);
            characters.put(cp, glyph);
            pos += height;
        }

        LOGGER.log(Level.DEBUG, "Loaded font {0} with {1} characters", fontName, characters.size());
        return new FigletFont(fontName, hardblank, height, baseline, comment, characters, rightToLeft, layout);
    }

    /**
     * Reads one glyph's rows starting at {@code pos}, stripping the end mark
     * from each row (see {@link #stripEndMark(String)}).
     *
     * @param lines      all lines of the font file
     * @param pos        index of the first row of this glyph
     * @param height     number of rows per glyph
     * @param fontName   font name, used for error messages
     * @param cp         the code point being read, used for error messages
     * @return the glyph's rows, with end-markers stripped; hardblanks are kept, since
     *         the renderer must tell them apart from blanks when fitting glyphs
     * @throws FigletException if the file ends before {@code height} rows could be read
     */
    private static char[][] readGlyph(List<String> lines, int pos, int height,
                                       String fontName, int cp) {
        char[][] rows = new char[height][];
        for (int row = 0; row < height; row++) {
            if (pos + row >= lines.size()) {
                throw new FigletException(String.format(
                    "Font '%s': premature end of file while reading glyph for codepoint U+%04X", fontName, cp));
            }
            rows[row] = stripEndMark(lines.get(pos + row)).toCharArray();
        }
        return rows;
    }

    /**
     * Strips the end mark from a glyph row, as the original FIGlet does:
     * trailing whitespace is ignored, the last remaining character is the end
     * mark, and all of its trailing repetitions are removed. The end mark is
     * thus determined per row, which also covers fonts that use a different
     * end mark for some characters, or a single mark on the last row.
     *
     * @param line one row of a glyph as read from the font file
     * @return the row content without end mark
     */
    static String stripEndMark(String line) {
        int end = line.length();
        while (end > 0 && isBlank(line.charAt(end - 1))) {
            end--;
        }
        if (end > 0) {
            char endMark = line.charAt(end - 1);
            while (end > 0 && line.charAt(end - 1) == endMark) {
                end--;
            }
        }
        return line.substring(0, end);
    }

    /**
     * Returns whether {@code ch} counts as whitespace after an end mark,
     * matching the C {@code isspace} function for ASCII.
     *
     * @param ch a character
     * @return {@code true} for space, tab, vertical tab and form feed
     */
    private static boolean isBlank(char ch) {
        return ch == ' ' || ch == '\t' || ch == '\013' || ch == '\f';
    }

    /**
     * Determines the horizontal layout bits (see {@link FigletFont#layout()}) from the header fields.
     * <p>
     * The optional {@code full_layout} field takes precedence; only its
     * horizontal bits are used. Without it, {@code old_layout} -1 means full
     * width, 0 fitting, and a positive value smushing with the rules given by
     * its bits. As in the original FIGlet, the hardblank rule (32) is not taken
     * from {@code old_layout}, so a value of 32 means universal smushing.
     * Fields that are not numeric are ignored, falling back to fitting.
     *
     * @param parts the header fields following the signature and hardblank
     * @return the layout bits
     */
    private static int layout(String[] parts) {
        if (parts.length > 6) {
            try {
                return Integer.parseInt(parts[6]) & 0xFF;
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.DEBUG, "Ignoring non-numeric full_layout {0}", parts[6]);
            }
        }
        try {
            int oldLayout = Integer.parseInt(parts[3]);
            if (oldLayout < 0) {
                return 0;
            }
            return oldLayout == 0 ? FigletFont.LAYOUT_FITTING : (oldLayout & 31) | FigletFont.LAYOUT_SMUSHING;
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.DEBUG, "Ignoring non-numeric old_layout {0}", parts[3]);
            return FigletFont.LAYOUT_FITTING;
        }
    }

    private static int parseCodepoint(String tag) {
        String[] parts = tag.split("\\s+", 2);
        String cpStr = parts[0];
        try {
            if (cpStr.startsWith("0x") || cpStr.startsWith("0X")) {
                return Integer.parseInt(cpStr.substring(2), 16);
            } else if (cpStr.startsWith("0") && cpStr.length() > 1) {
                return Integer.parseInt(cpStr.substring(1), 8);
            } else {
                return Integer.parseInt(cpStr);
            }
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Reads all lines from the specified input stream.
     *
     * <p>
     * Decodes bytes into text using UTF-8 encoding with a fallback to ISO-8859-1
     * if malformed or invalid byte sequences are encountered.
     *
     * @param is input stream to read lines from
     * @return list of lines read from the stream
     * @throws FigletException if an I/O error occurs while reading the stream
     */
    private static List<String> readLines(InputStream is) {
        byte[] bytes;
        try {
            bytes = is.readAllBytes();
        } catch (IOException ex) {
            throw new FigletException("Failed to read font stream", ex);
        }

        // try strict utf-8 decoding first
        CharsetDecoder utf8Decoder = UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            CharBuffer charBuffer = utf8Decoder.decode(ByteBuffer.wrap(bytes));
            return charBuffer.toString().lines().collect(Collectors.toCollection(ArrayList::new));
        } catch (CharacterCodingException ex) {
            // fallback to iso-8859-1 for legacy 8-bit fonts
            LOGGER.log(Level.DEBUG, "Font stream is not valid UTF-8, falling back to ISO-8859-1");
            String content = new String(bytes, ISO_8859_1);
            return content.lines().collect(Collectors.toCollection(ArrayList::new));
        }
    }

}
