package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Unit tests for {@link FigletFontLoader}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletFontLoaderTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "flf2a$ 5 4 10 0 3\nComment 1\nComment 2\nComment 3\n", // 5 Parameter
        "flf2a$ 5 4 10 0 3 0\nComment 1\nComment 2\nComment 3\n" // 6 Parameter
    })
    void loadFromStream_differentHeaderParamCounts_parsesHeaderCorrectly(String headerAndComments) {
        String s = headerAndComments + " @\n @\n @\n @\n @@\n".repeat(102);

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("TestFont", is);

        assertThat(font)
            .isNotNull()
            .satisfies(f -> assertThat(f.getHeight()).isEqualTo(5))
            .satisfies(f -> assertThat(f.getBaseline()).isEqualTo(4))
            .satisfies(f -> assertThat(f.getComment()).hasSize(3));
    }

    @Test
    void privateConstructor_throwsUnsupportedOperationException() throws NoSuchMethodException {
        Constructor<?> constructor = FigletFontLoader.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Utility class %s cannot be instantiated", FigletFontLoader.class.getSimpleName());
    }

    @Test
    void loadFromStream_nullFontName_throwsNullPointerException() {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream(null, is))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fontName");
    }

    @Test
    void loadFromStream_nullInputStream_throwsNullPointerException() {
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("font", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("is");
    }

    @Test
    void loadFromStream_emptyStream_throwsFigletException() {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("EmptyFont", is))
            .isInstanceOf(FigletException.class)
            .hasMessage("Font file is empty: EmptyFont");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "this is not a font\n",
        "xyz2a$ 5 4 10 0 0\n",
        "FLF2A$ 5 4 10 0 0\n" // wrong case is not the magic signature
    })
    void loadFromStream_missingSignature_throwsFigletException(String content) {
        InputStream is = new ByteArrayInputStream(content.getBytes(ISO_8859_1));
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("BadFont", is))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("signature")
            .hasMessageContaining("BadFont");
    }

    @Test
    void loadFromStream_tooFewHeaderParameters_throwsFigletException() {
        String content = "flf2a$ 5 4 10\n"; // only 3 params after hardblank
        InputStream is = new ByteArrayInputStream(content.getBytes(ISO_8859_1));
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("BadHeader", is))
            .isInstanceOf(FigletException.class)
            .hasMessage("Malformed font header in font: BadHeader");
    }

    @Test
    void loadFromStream_commentBlockExceedsFileLength_throwsFigletException() {
        // commentLines=5 but only header + 1 line follow
        String content = "flf2a$ 5 4 10 0 5\nOnly one comment line\n";
        InputStream is = new ByteArrayInputStream(content.getBytes(ISO_8859_1));
        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("Truncated", is))
            .isInstanceOf(FigletException.class)
            .hasMessage("Font file too short to contain comment block: Truncated");
    }

    @Test
    void loadFromStream_truncationAtGlyphBoundary_stopsEarlyWithoutException() {
        // header declares 0 comment lines and provides exactly one full glyph,
        // then nothing more -> loader stops leniently at the glyph boundary.
        String s = "flf2a$ 2 1 10 0 0\n"
            + " @\n @@\n"; // first glyph (space), complete - then EOF

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("Truncated2", is);

        assertThat(font).isNotNull();
        // only the first (space) glyph could be fully read
        assertThat(font.getCharacters()).hasSize(1);
        assertThat(font.supportsCodepoint(' ')).isTrue();
    }

    @Test
    void loadFromStream_truncationMidGlyph_throwsFigletException() {
        // Second glyph is started but cut off mid-way through its rows
        String s = "flf2a$ 2 1 10 0 0\n" + " @\n @@\n" // first glyph (space), complete
            + " @\n"; // second glyph, only 1 of 2 rows -> truncated mid-glyph

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));

        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("Truncated3", is))
            .isInstanceOf(FigletException.class)
            .hasMessageStartingWith("Font 'Truncated3': premature end of file");
    }

    @Test
    void loadFromStream_emptyLastRowOfFirstGlyph_throwsFigletException() {
        // First (space) glyph has height=2, but its second (last) row is an empty
        // line -> the end-of-character marker cannot be reliably detected.
        String s = "flf2a$ 2 1 10 0 0\n" + " @\n" + "\n";

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));

        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("EmptyMarkerRow", is))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("EmptyMarkerRow")
            .hasMessageContaining("cannot detect the end-of-character marker")
            .hasMessageContaining("empty");
    }

    @Test
    void loadFromStream_fileTooShortForFirstGlyph_throwsFigletException() {
        // Header declares height=5, but only one row of the first glyph follows
        // -> not enough lines exist to detect the end-of-character marker.
        String s = "flf2a$ 5 4 10 0 0\n @\n";

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));

        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("TooShortForMarker", is))
            .isInstanceOf(FigletException.class)
            .hasMessageContaining("TooShortForMarker")
            .hasMessageContaining("file too short");
    }

    @Test
    void loadFromStream_singleMarkerOnLastRowOfHeightOneFont_loadsLeniently() {
        // Real-world fonts (e.g. morse.flf, term.flf, binary.flf, rot13.flf) use
        // height=1, where every row is simultaneously the "last row" of its glyph
        // and would formally be expected to end in a doubled marker. These fonts
        // only use a single trailing marker instead. The loader must remain
        // lenient here and load such fonts without throwing.
        String s = "flf2a$ 1 1 10 0 0\n" + " @\n".repeat(102);

        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("SingleMarkerFont", is);

        assertThat(font).isNotNull();
        assertThat(font.getCharacters()).hasSize(102);
        char[][] spaceGlyph = font.getCharacter(' ');
        assertThat(spaceGlyph).isNotNull();
        assertThat(new String(spaceGlyph[0])).isEqualTo(" ");
    }

    @Test
    void loadFromStream_tlfSignature_acceptsFont() {
        String s = "tlf2a$ 1 0 10 0 0\n" + " @@\n".repeat(102); // height = 1
        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("ToiletFont", is);

        assertThat(font).isNotNull();
        assertThat(font.getHeight()).isEqualTo(1);
    }

    @Test
    void loadFromStream_hardblankCharacter_replacesWithSpace() {
        // hardblank = '$', glyph row contains '$' which must become ' '
        String s = "flf2a$ 1 0 10 0 0\n" + "$$@@\n" + " @@\n".repeat(101);
        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("HardblankFont", is);

        char[][] spaceGlyph = font.getCharacter(' ');
        assertThat(spaceGlyph).isNotNull();
        assertThat(new String(spaceGlyph[0])).isEqualTo("  ");
    }

    @Test
    void loadFromStream_nonAtEndMarker_detectsAndStripsMarker() {
        // Use '#' as the row end marker, detected from the last row of the first glyph
        String s = "flf2a# 2 1 10 0 0\n" + "XX#\nXX##\n" + "XX#\nXX##\n".repeat(101);
        InputStream is = new ByteArrayInputStream(s.getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("HashMarkerFont", is);

        char[][] glyph = font.getCharacter(' ');
        assertThat(glyph).isNotNull();
        assertThat(new String(glyph[0])).isEqualTo("XX");
        assertThat(new String(glyph[1])).isEqualTo("XX");
    }

    @Test
    void loadFromStream_utf8EncodingWithMultibyteCharacters_parsesCorrectly() {
        String s = "flf2a$ 1 0 10 0 1\n"
            + "UTF-8 Comment with Greek alpha: α\n"
            + " @@\n".repeat(102)
            + "0x03B1 greek alpha\n" + "α@@\n";

        InputStream is = new ByteArrayInputStream(s.getBytes(UTF_8));
        FigletFont font = FigletFontLoader.loadFromStream("Utf8Font", is);

        assertThat(font).isNotNull();
        assertThat(font.getComment()).contains("UTF-8 Comment with Greek alpha: α");
        assertThat(font.supportsCodepoint(0x03B1)).isTrue();
    }

    @Test
    void loadFromStream_invalidUtf8Bytes_fallbacksToIso88591() {
        byte[] headerBytes = "flf2a$ 1 0 10 0 1\n".getBytes(ISO_8859_1);
        byte[] commentBytes = new byte[] {(byte) 0xE4, '\n'};
        byte[] glyphBytes = " @@\n".repeat(102).getBytes(ISO_8859_1);

        byte[] combined = new byte[headerBytes.length + commentBytes.length + glyphBytes.length];
        System.arraycopy(headerBytes, 0, combined, 0, headerBytes.length);
        System.arraycopy(commentBytes, 0, combined, headerBytes.length, commentBytes.length);
        System.arraycopy(glyphBytes, 0, combined, headerBytes.length + commentBytes.length, glyphBytes.length);

        InputStream is = new ByteArrayInputStream(combined);
        FigletFont font = FigletFontLoader.loadFromStream("Iso88591Font", is);

        assertThat(font).isNotNull();
        assertThat(font.getComment()).contains("ä");
    }

    @Test
    void loadFromStream_taggedUnicodeDecimal_parsesCodepoint() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(1);
        sb.append("9786 SMILEY\n"); // decimal codepoint tag with trailing comment
        sb.append(" @@\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("TaggedFont", is);

        assertThat(font.supportsCodepoint(9786)).isTrue();
    }

    @Test
    void loadFromStream_taggedUnicodeHex_parsesCodepoint() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(1);
        sb.append("0x263A SMILEY\n"); // hex codepoint tag
        sb.append(" @@\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("TaggedFontHex", is);

        assertThat(font.supportsCodepoint(0x263A)).isTrue();
    }

    @Test
    void loadFromStream_taggedUnicodeOctal_parsesCodepoint() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(1);
        sb.append("0142 OCTAL TAG\n"); // octal codepoint tag -> decimal 98
        sb.append(" @@\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("TaggedFontOctal", is);

        assertThat(font.supportsCodepoint(98)).isTrue();
    }

    @Test
    void loadFromStream_invalidTagLine_skipsLine() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(1);
        sb.append("not-a-number\n"); // invalid tag, should be skipped
        sb.append("9786\n");
        sb.append(" @@\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("InvalidTagFont", is);

        assertThat(font.supportsCodepoint(9786)).isTrue();
    }

    @Test
    void loadFromStream_blankLinesBetweenTags_skipsBlankLines() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(1);
        sb.append("\n"); // blank line between glyph data and tag
        sb.append("9786\n");
        sb.append(" @@\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("BlankBetweenTags", is);

        assertThat(font.supportsCodepoint(9786)).isTrue();
    }

    @Test
    void loadFromStream_taggedCharacterWithTruncatedGlyph_ignoresCharacter() {
        StringBuilder sb = buildMinimalHeaderWithRequiredGlyphs(2);
        sb.append("9786\n");
        sb.append(" @\n");

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("TruncatedTag", is);

        assertThat(font.supportsCodepoint(9786)).isFalse();
    }

    @Test
    void loadFromFile_nullPath_throwsNullPointerException() {
        assertThatThrownBy(() -> FigletFontLoader.loadFromFile(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("path");
    }

    @Test
    void loadFromFile_nonExistentFile_throwsFigletException(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.flf");
        assertThatThrownBy(() -> FigletFontLoader.loadFromFile(missing))
            .isInstanceOf(FigletException.class)
            .hasMessage("Font file not found: %s", missing);
    }

    @Test
    void loadFromFile_validFile_derivesNameFromFileName(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("myfont.flf");
        Files.writeString(file, buildMinimalHeaderWithRequiredGlyphs(1).toString());

        FigletFont font = FigletFontLoader.loadFromFile(file);

        assertThat(font.getName()).isEqualTo("myfont");
    }

    @Test
    void loadBuiltin_nullFontName_throwsNullPointerException() {
        assertThatThrownBy(() -> FigletFontLoader.loadBuiltin(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontName");
    }

    @Test
    void loadBuiltin_unknownFont_throwsFigletException() {
        assertThatThrownBy(() -> FigletFontLoader.loadBuiltin("no-such-builtin-font-xyz"))
            .isInstanceOf(FigletException.class)
            .hasMessageStartingWith("Bundled font not found on classpath: ");
    }

    @Test
    void loadBuiltin_standardFont_loadsSuccessfully() {
        FigletFont font = FigletFontLoader.loadBuiltin("standard");
        assertThat(font).isNotNull();
        assertThat(font.getHeight()).isGreaterThan(0);
    }

    @Test
    void loadBuiltin_resourcePathNotOnClasspath_throwsFigletException() {
        assertThatThrownBy(() -> FigletFontLoader.loadBuiltin("phantom", "fonts/does-not-exist.flf"))
            .isInstanceOf(FigletException.class)
            .hasMessage("Bundled font resource not found on classpath: fonts/does-not-exist.flf");
    }

    @Test
    void loadFromFile_unreadableFile_throwsFigletException(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
            "requires a POSIX file system");
        Assumptions.assumeTrue(!System.getProperty("user.name").equals("root"),
            "root bypasses file permission checks");

        Path unreadable = tmp.resolve("unreadable.flf");
        Files.writeString(unreadable, buildMinimalHeaderWithRequiredGlyphs(1).toString());
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));

        try {
            assertThatThrownBy(() -> FigletFontLoader.loadFromFile(unreadable))
                .isInstanceOf(FigletException.class)
                .hasMessage("Font file not readable: %s", unreadable);
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    void loadFromFile_directoryInsteadOfFile_throwsFigletException(@TempDir Path tmp) throws Exception {
        // exists() and isReadable() both report true for a directory, so loadFromFile()
        // proceeds to open it; reading its content then fails and is reported as a
        // FigletException (surfaced by the underlying readLines() stream-read failure).
        Path dir = tmp.resolve("adir.flf");
        Files.createDirectories(dir);

        assertThatThrownBy(() -> FigletFontLoader.loadFromFile(dir))
            .isInstanceOf(FigletException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "NoHardblank|flf2a|Malformed font header (missing hardblank character) in font: NoHardblank",
        "NonNumeric|flf2a$ five 4 10 0 0|Malformed font header (non-numeric field) in font: NonNumeric",
        "ZeroHeight|flf2a$ 0 0 10 0 0|Malformed font header (height must be a positive number, was 0) in font: ZeroHeight",
        "NegativeComments|flf2a$ 5 4 10 0 -1|Malformed font header (comment_lines must not be negative, was -1) in font: NegativeComments"
    })
    void loadFromStream_malformedHeader_throwsFigletExceptionWithExpectedMessage(
            String fontName, String header, String expectedMessage) {
        InputStream is = new ByteArrayInputStream(header.getBytes(ISO_8859_1));

        assertThatThrownBy(() -> FigletFontLoader.loadFromStream(fontName, is))
            .isInstanceOf(FigletException.class)
            .hasMessage(expectedMessage);
    }

    @Test
    void loadFromStream_streamThrowsIOExceptionOnRead_throwsFigletException() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("boom");
            }

            @Override
            public int read(byte[] b, int off, int len) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }
        };

        assertThatThrownBy(() -> FigletFontLoader.loadFromStream("FailingStream", failing))
            .isInstanceOf(FigletException.class)
            .hasMessage("Failed to read font stream")
            .cause()
            .hasMessage("boom");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "1 | 0",
        "2 | 1",
        "6 | 5"
    })
    void loadFromStream_variousHeightAndBaseline_parsesHeaderCorrectly(int height, int baseline) {
        StringBuilder sb = new StringBuilder("flf2a$ ")
            .append(height).append(" ").append(baseline).append(" 10 0 0\n");
        for (int i = 0; i < 102; i++) {
            sb.append(" @\n".repeat(Math.max(0, height - 1)));
            sb.append(" @@\n");
        }

        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(ISO_8859_1));
        FigletFont font = FigletFontLoader.loadFromStream("ParamFont", is);

        assertThat(font.getHeight()).isEqualTo(height);
        assertThat(font.getBaseline()).isEqualTo(baseline);
    }

    /**
     * Builds a minimal valid FLF header followed by 102 required glyphs of
     * the given height, each consisting of a single space row(s) terminated
     * by {@code @}/{@code @@}.
     */
    private static StringBuilder buildMinimalHeaderWithRequiredGlyphs(int height) {
        StringBuilder sb = new StringBuilder("flf2a$ ")
            .append(height).append(" ").append(Math.max(0, height - 1)).append(" 10 0 0\n");
        for (int i = 0; i < 102; i++) {
            sb.append(" @\n".repeat(Math.max(0, height - 1)));
            sb.append(" @@\n");
        }
        return sb;
    }

}
