package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link FigletFontType}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletFontTypeTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "FIGFONT     | .flf | FIGlet font",
        "TOILET_FONT | .tlf | TOIlet font"
    })
    void enumConstants_shouldHaveExpectedExtensionAndDescription(
            FigletFontType type, String extension, String description) {
        assertThat(type.getExtension()).isEqualTo(extension);
        assertThat(type.getDescription()).isEqualTo(description);
    }

    @ParameterizedTest
    @ValueSource(strings = {"font.flf", "font.tlf", "FONT.FLF", "FONT.TLF", "Font.Flf"})
    void isSupported_supportedExtensions_shouldReturnTrue(String fileName) {
        assertThat(FigletFontType.isSupported(fileName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"font.txt", "font", "font.md", "font.flfx"})
    void isSupported_unsupportedExtensions_shouldReturnFalse(String fileName) {
        assertThat(FigletFontType.isSupported(fileName)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "standard.flf | standard",
        "STANDARD.FLF | STANDARD",
        "mono9.tlf    | mono9",
        "Mono9.TLF    | Mono9"
    })
    void removeExtension_knownExtensions_shouldStripExtension(String fileName, String expected) {
        assertThat(FigletFontType.removeExtension(fileName)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"readme.txt", "noextension", "font.foo"})
    void removeExtension_unknownExtension_shouldReturnUnchanged(String fileName) {
        assertThat(FigletFontType.removeExtension(fileName)).isEqualTo(fileName);
    }

    @ParameterizedTest
    @EnumSource(FigletFontType.class)
    void allEnumValues_shouldHaveNonBlankExtensionAndDescription(FigletFontType type) {
        assertThat(type.getExtension()).startsWith(".");
        assertThat(type.getDescription()).isNotBlank();
    }

}
