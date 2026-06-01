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
 * Each character ends with a line whose last non-whitespace character is {@code @};
 * the final row of a character ends with {@code @@}.
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
            "Utility class " + getClass().getSimpleName() + " cannot be instantiated");
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
        String resourcePath = FigletFontRegistry.getBuiltinFonts().get(fontName);
        if (resourcePath != null) {
            return loadBuiltin(fontName, resourcePath);
        }
        throw new FigletException("Bundled font not found on classpath: " + fontName);
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
                throw new FigletException("Failed to read bundled font: " + resourcePath, ex);
            }
        }

        throw new FigletException("Bundled font resource not found on classpath: " + resourcePath);
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
            throw new FigletException("Font file not found: " + path);
        } else if (!Files.isReadable(path)) {
            throw new FigletException("Font file not readable: " + path);
        }

        String fileName = path.getFileName().toString();
        String name = FigletFontType.removeExtension(fileName);

        LOGGER.log(Level.INFO, "Loading font from file: {0}", path);
        try (InputStream is = Files.newInputStream(path)) {
            return parse(name, is);
        } catch (IOException ex) {
            throw new FigletException("Failed to read font file: " + path, ex);
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
            throw new FigletException("Font file is empty: " + fontName);
        }

        // header
        String header = lines.get(0);
        String magic;
        if (header.startsWith(MAGIC_FLF)) {
            magic = MAGIC_FLF;
        } else if (header.startsWith(MAGIC_TLF)) {
            magic = MAGIC_TLF;
        } else {
            throw new FigletException("Not a valid FIGfont or TOIlet font (missing signature): " + fontName);
        }

        if (header.length() <= magic.length()) {
            throw new FigletException("Malformed font header (missing hardblank character) in font: " + fontName);
        }
        char hardblank = header.charAt(magic.length());
        String[] parts = header.substring(magic.length() + 1).trim().split("\\s+");
        if (parts.length < 5) {
            throw new FigletException("Malformed font header in font: " + fontName);
        }

        int height;
        int baseline;
        int commentLines;
        try {
            height = Integer.parseInt(parts[0]);
            baseline = Integer.parseInt(parts[1]);
            commentLines = Integer.parseInt(parts[4]);
        } catch (NumberFormatException ex) {
            throw new FigletException("Malformed font header (non-numeric field) in font: " + fontName, ex);
        }

        if (height <= 0) {
            throw new FigletException(
                "Malformed font header (height must be a positive number, was " + height + ") in font: " + fontName);
        }
        if (commentLines < 0) {
            throw new FigletException(
                "Malformed font header (comment_lines must not be negative, was " + commentLines + ") in font: " + fontName);
        }

        // comment block
        int dataStart = 1 + commentLines;
        if (dataStart > lines.size()) {
            throw new FigletException("Font file too short to contain comment block: " + fontName);
        }

        List<String> rawComment = lines.subList(1, dataStart);
        List<String> comment = FigletFontCommentNormalizer.normalizeComment(fontName, rawComment);

        // End marker detection:
        // The FIGfont spec does not mandate '@' as the row end marker; each font
        // may use a different character. The marker is defined implicitly as the
        // last character of the last row of the first (space) glyph.
        // We detect it here before reading any glyph data.
        char endMarker = detectEndMarker(lines, dataStart, height, fontName);

        // character data
        Map<Integer, char[][]> characters = new LinkedHashMap<>();
        int pos = dataStart;

        // required characters in fixed order: ASCII 32-126, then Deutsch extras
        for (int cp : REQUIRED_CODEPOINTS) {
            if (pos >= lines.size()) {
                break; // truncated font - be lenient, stop early
            }
            char[][] glyph = readGlyph(lines, pos, height, hardblank, endMarker, fontName, cp);
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
            char[][] glyph = readGlyph(lines, pos, height, hardblank, endMarker, fontName, cp);
            characters.put(cp, glyph);
            pos += height;
        }

        LOGGER.log(Level.DEBUG, "Loaded font {0} with {1} characters", fontName, characters.size());
        return new FigletFont(fontName, hardblank, height, baseline, comment, characters);
    }

    /**
     * Detects the row end-marker character from the last row of the first glyph.
     * Most fonts use {@code @}, but some (e.g. cosmic, computer) use a different char.
     *
     * @throws FigletException if the first character definition is missing or its
     *                          last row is empty, so the marker cannot be reliably determined
     */
    private static char detectEndMarker(List<String> lines, int dataStart, int height, String name) {
        int lastRowIndex = dataStart + height - 1;
        if (lastRowIndex >= lines.size()) {
            throw new FigletException(String.format(
                "Font '%s': file too short to contain the first (space) character definition "
                + "needed to detect the end-of-character marker (expected %d row(s) starting at line %d, "
                + "but the file has only %d line(s))",
                name, height, dataStart + 1, lines.size()));
        }
        String lastRow = lines.get(lastRowIndex);
        if (lastRow.isEmpty()) {
            throw new FigletException(String.format(
                "Font '%s': cannot detect the end-of-character marker because the last row of the "
                + "first character definition (line %d) is empty", name, lastRowIndex + 1));
        }
        return lastRow.charAt(lastRow.length() - 1);
    }

    /**
     * Reads one glyph's rows starting at {@code pos}, stripping the trailing
     * end-marker character(s) from each row.
     * <p>
     * Per the FIGfont convention, a row is expected to end in a single
     * occurrence of {@code endMarker}, and the last row of a character in a
     * double occurrence. However, this is <em>intentionally</em> enforced
     * leniently: the loop below strips up to the expected number of trailing
     * marker characters but does not fail if fewer are found. Several
     * real-world, otherwise valid FIGfont files bundled with this project
     * (e.g. {@code morse.flf}, {@code term.flf}, {@code binary.flf},
     * {@code rot13.flf} — all {@code height=1} fonts whose single row is
     * simultaneously the "last row" of the glyph — and {@code doh.flf}, whose
     * {@code '@'} glyph switches to a single {@code '#'} terminator to avoid
     * clashing with the glyph's own {@code '@'}-heavy artwork) only use a
     * single trailing marker even where two would be expected. Rejecting
     * these rows outright would make such fonts fail to load entirely, even
     * though the content itself is unambiguous and renders correctly.
     *
     * @param lines      all lines of the font file
     * @param pos        index of the first row of this glyph
     * @param height     number of rows per glyph
     * @param hardblank  the font's hardblank character, replaced with a real space
     * @param endMarker  the detected end-of-row / end-of-character marker character
     * @param fontName   font name, used for error messages
     * @param cp         the code point being read, used for error messages
     * @return the glyph's rows, with end-markers stripped and hardblanks replaced
     * @throws FigletException if the file ends before {@code height} rows could be read
     */
    private static char[][] readGlyph(List<String> lines, int pos, int height,
                                       char hardblank, char endMarker, String fontName, int cp) {
        char[][] rows = new char[height][];
        for (int row = 0; row < height; row++) {
            if (pos + row >= lines.size()) {
                throw new FigletException(String.format(
                    "Font '%s': premature end of file while reading glyph for codepoint U+%04X", fontName, cp));
            }
            String line = lines.get(pos + row);
            // strip trailing end-markers (single marker = end-of-row, double = end-of-char);
            // intentionally lenient — see method javadoc for why fewer markers than
            // expected must not be treated as an error
            int endAt = line.length();
            int expectedMarkers = (row == height - 1) ? 2 : 1;
            while (expectedMarkers > 0 && endAt > 0 && line.charAt(endAt - 1) == endMarker) {
                endAt--;
                expectedMarkers--;
            }
            String rowContent = line.substring(0, endAt);
            // replace hardblank with actual space
            rows[row] = rowContent.replace(hardblank, ' ').toCharArray();
        }
        return rows;
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
