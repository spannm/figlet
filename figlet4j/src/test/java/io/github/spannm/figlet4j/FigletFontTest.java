package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for {@link FigletFont}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletFontTest {

    private final char[][] aGlyph = new char[][] {
        {' ', '_', ' '},
        {'|', ' ', '|'}
    };
    private final char[][]   bGlyph = new char[][] {
        {'|', '\\'},
        {'|', '/'}
    };
    private final FigletFont defaultFont = new FigletFont(
        "standard",
        '$',
        2,
        1,
        List.of("Line 1", "Line 2"),
        Map.of(
            (int) 'A', aGlyph,
            (int) 'B', bGlyph
        )
    );

    @Test
    void constructor_nullFontName_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new FigletFont(null, '$', 2, 1, List.of(), Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontName");
    }

    @Test
    void constructor_nullCharacters_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new FigletFont("font", '$', 2, 1, List.of(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("characters");
    }

    @Test
    void getName_shouldReturnFontName() {
        assertThat(defaultFont.getName()).isEqualTo("standard");
    }

    @Test
    void getHardblank_shouldReturnHardblankChar() {
        assertThat(defaultFont.getHardblank()).isEqualTo('$');
    }

    @Test
    void getHeight_shouldReturnHeight() {
        assertThat(defaultFont.getHeight()).isEqualTo(2);
    }

    @Test
    void getBaseline_shouldReturnBaseline() {
        assertThat(defaultFont.getBaseline()).isEqualTo(1);
    }

    @Test
    void getComment_shouldReturnUnmodifiableCommentLines() {
        List<String> comment = defaultFont.getComment();

        assertThat(comment).containsExactly("Line 1", "Line 2");
        assertThatThrownBy(() -> comment.add("Line 3"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "65 | true",  // 'A'
        "66 | true",  // 'B'
        "67 | false"  // 'C'
    })
    void supportsCodepoint_shouldReturnExpectedResult(int codepoint, boolean expected) {
        assertThat(defaultFont.supportsCodepoint(codepoint)).isEqualTo(expected);
    }

    @Test
    void supportsCodepoint_emptyGlyph_false() {
        FigletFont font = new FigletFont("empty", '$', 2, 1, List.of(), Map.of(0xE4, new char[][] {{}, {}}));

        assertThat(font.supportsCodepoint(0xE4)).isFalse();
        assertThat(font.getCharacter(0xE4)).isDeepEqualTo(new char[][] {{}, {}});
    }

    @Test
    void getSupportedCodepoints_mixedGlyphs_sortedWithoutEmptyGlyphs() {
        Map<Integer, char[][]> chars = new LinkedHashMap<>();
        chars.put((int) 'B', bGlyph);
        chars.put(0xE4, new char[][] {{}, {}});
        chars.put((int) ' ', new char[][] {{'$'}, {'$'}});
        chars.put((int) 'A', aGlyph);
        FigletFont font = new FigletFont("mixed", '$', 2, 1, List.of(), chars);

        assertThat(font.getSupportedCodepoints()).containsExactly((int) ' ', (int) 'A', (int) 'B');
        assertThatThrownBy(() -> font.getSupportedCodepoints().add((int) 'C'))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "AB            | \"\"",
        "\"A\nB\r\n\"  | \"\"",
        "ACAC          | 67",
        "Z\u00e4BZ     | 90 228",
        "A\uD83D\uDE00 | 128512"
    })
    void findUnsupportedCodepoints_text_distinctInOrderOfOccurrence(String text, String expected) {
        Integer[] codepoints = expected.isEmpty()
            ? new Integer[0]
            : Arrays.stream(expected.split(" ")).map(Integer::valueOf).toArray(Integer[]::new);

        assertThat(defaultFont.findUnsupportedCodepoints(text)).containsExactly(codepoints);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "3 | 223 | false", // a plain character in a glyph of three rows is a placeholder
        "2 | 223 | true",  // in a font of two rows it is a legitimate glyph
        "3 | 46  | true"   // ASCII characters are never placeholders, e.g. a plain '.'
    })
    void supportsCodepoint_glyphOfItsCharacterOnly_placeholderInHighFonts(int height, int codepoint, boolean expected) {
        char[][] rows = new char[height][0];
        rows[0] = new char[] {' ', (char) codepoint, '$'};
        FigletFont font = new FigletFont("lazy", '$', height, 1, List.of(), Map.of(codepoint, rows));

        assertThat(font.supportsCodepoint(codepoint)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
        "''                       | none",
        "32-126                   | all of ASCII",
        "32-126 196 214 220 223   | all of ASCII, plus \u00c4 \u00d6 \u00dc \u00df",
        "32-126 127-255           | all of ASCII, Latin-1 Supplement",
        "32-126 160 173 256-383   | all of ASCII, Latin Extended-A, plus U+00A0 U+00AD",
        "32-126 160-180           | all of ASCII, plus 21 of Latin-1 Supplement",
        "32-126 160-180 913-933   | all of ASCII, plus 21 of Latin-1 Supplement, 21 of Greek",
        "33-47 58-126             | ASCII (without space 0-9)",
        "32 65-90 97-122          | ASCII (only space A-Z a-z)",
        "32-34 36-126 12288-12291 | \"ASCII (without #), plus U+3000 \u3001 \u3002 \u3003\""
    })
    void getCharacterSummary_codepoints_readableSummary(String ranges, String expected) {
        Map<Integer, char[][]> chars = new LinkedHashMap<>();
        for (String range : ranges.split(" ", -1)) {
            if (!range.isEmpty() && !"''".equals(range)) {
                String[] bounds = range.split("-", -1);
                int end = Integer.parseInt(bounds[bounds.length - 1]);
                for (int cp = Integer.parseInt(bounds[0]); cp <= end; cp++) {
                    chars.put(cp, bGlyph);
                }
            }
        }
        FigletFont font = new FigletFont("summary", '$', 2, 1, List.of(), chars);

        assertThat(font.getCharacterSummary()).isEqualTo(expected);
    }

    @Test
    void getCharacterSummary_manyIncompleteBlocks_countedInTotal() {
        Map<Integer, char[][]> chars = new LinkedHashMap<>();
        for (int cp : new int[] {0xA1, 0x100, 0x180, 0x250, 0x391, 0x401}) {
            for (int i = 0; i < 5; i++) {
                chars.put(cp + i, bGlyph);
            }
        }
        FigletFont font = new FigletFont("many", '$', 2, 1, List.of(), chars);

        assertThat(font.getCharacterSummary()).isEqualTo("plus 30 more in 6 blocks");
    }

    @Test
    void getCharacter_existingCodepoint_shouldReturnGlyphMatrix() {
        assertThat(defaultFont.getCharacter('A')).isDeepEqualTo(aGlyph);
    }

    @Test
    void getCharacter_nonExistingCodepoint_shouldReturnNull() {
        assertThat(defaultFont.getCharacter('Z')).isNull();
    }

    @Test
    void getCharacters_shouldReturnUnmodifiableMapOfAllCharacters() {
        Map<Integer, char[][]> characters = defaultFont.getCharacters();

        assertThat(characters)
            .hasSize(2)
            .containsKeys((int) 'A', (int) 'B');

        assertThatThrownBy(() -> characters.put((int) 'C', new char[0][0]))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getCharacter_mutatingReturnedArray_shouldNotAffectFont() {
        char[][] returned = defaultFont.getCharacter('A');
        returned[0][0] = 'X';

        assertThat(defaultFont.getCharacter('A')).isDeepEqualTo(aGlyph);
    }

    @Test
    void constructor_mutatingInputArrayAfterConstruction_shouldNotAffectFont() {
        char[][] inputGlyph = {
            {'#', '#'},
            {'#', '#'}
        };
        FigletFont font = new FigletFont("mutTest", '$', 2, 1, List.of(), Map.of((int) 'M', inputGlyph));

        inputGlyph[0][0] = 'X';

        assertThat(font.getCharacter('M')).isDeepEqualTo(new char[][] {
            {'#', '#'},
            {'#', '#'}
        });
    }

    @Test
    void getCharacter_glyphWithHardblanks_returnsSpaces() {
        FigletFont font = new FigletFont("hb", '$', 1, 1, List.of(), Map.of((int) 'X', new char[][] {{'$', 'X', '$'}}));

        assertThat(font.getCharacter('X')).isDeepEqualTo(new char[][] {{' ', 'X', ' '}});
        assertThat(font.getCharacters().get((int) 'X')).isDeepEqualTo(new char[][] {{' ', 'X', ' '}});
        assertThat(font.glyph('X')).isDeepEqualTo(new char[][] {{'$', 'X', '$'}});
    }

    @Test
    void toString_shouldReturnFormattedString() {
        String expected = "FigletFont[name='standard', hardblank='$', height=2, baseline=1, rightToLeft=false, layout=64, comment=2, characters=2, supported=2]";
        assertThat(defaultFont).hasToString(expected);
    }

}
