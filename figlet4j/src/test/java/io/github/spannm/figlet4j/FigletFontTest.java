package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    void toString_shouldReturnFormattedString() {
        String expected = "FigletFont[name='standard', hardblank='$', height=2, baseline=1, comment=2, characters=2]";
        assertThat(defaultFont.toString()).isEqualTo(expected);
    }

}
