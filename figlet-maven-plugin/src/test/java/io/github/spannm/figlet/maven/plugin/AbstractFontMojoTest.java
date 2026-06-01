package io.github.spannm.figlet.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.spannm.figlet4j.FigletFont;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unit tests for {@link AbstractFontMojo} and {@link AbstractFigletMojo}.
 * <p>
 * Both are package-private abstract classes with no {@code @Mojo} of their
 * own, so they are exercised here via the concrete {@link RenderMojo}
 * subclass, focusing only on behavior owned by the abstract base classes
 * ({@code getFont()}, {@code getFontFile()}, {@code isSkip()},
 * {@code resolveFont()}) rather than {@link RenderMojo}'s own rendering logic
 * (covered by {@code RenderMojoTest}).
 */
@SuppressWarnings("checkstyle:MethodName")
final class AbstractFontMojoTest extends AbstractFigletMojoTestBase {

    private RenderMojo   mojo;
    private CapturingLog log;

    @BeforeEach
    void setUp() {
        mojo = new RenderMojo();
        log  = installCapturingLog(mojo);
    }

    @Test
    void getFont_defaultsToConfiguredDefaultValue() {
        setField(mojo, "parmFont", AbstractFontMojo.DEFAULT_FONT);
        assertThat(mojo.getFont()).isEqualTo("standard");
    }

    @Test
    void getFont_reflectsConfiguredValue() {
        setField(mojo, "parmFont", "banner");
        assertThat(mojo.getFont()).isEqualTo("banner");
    }

    @Test
    void getFontFile_defaultsToNull() {
        assertThat(mojo.getFontFile()).isNull();
    }

    @Test
    void getFontFile_reflectsConfiguredValue() {
        File file = new File("some.flf");
        setField(mojo, "parmFontFile", file);
        assertThat(mojo.getFontFile()).isSameAs(file);
    }

    @Test
    void isSkip_defaultsToFalse() {
        setField(mojo, "parmSkip", false);
        assertThat(mojo.isSkip()).isFalse();
    }

    @Test
    void isSkip_reflectsConfiguredValue() {
        setField(mojo, "parmSkip", true);
        assertThat(mojo.isSkip()).isTrue();
    }

    @Test
    void resolveFont_fontFileConfigured_loadsExternalFontAndTakesPrecedence(@TempDir Path tmp) throws Exception {
        Path flfFile = tmp.resolve("external.flf");
        Files.writeString(flfFile, buildMinimalFlf(1));
        setField(mojo, "parmFontFile", flfFile.toFile());
        setField(mojo, "parmFont", AbstractFontMojo.DEFAULT_FONT);

        FigletFont font = mojo.resolveFont();

        assertThat(font).isNotNull();
        assertThat(font.getName()).isEqualTo("external");
        assertThat(log.infoContains("Using external font 'external'")).isTrue();
    }

    @Test
    void resolveFont_fontFileAndNonDefaultFontBothConfigured_logsPrecedenceDebugMessage(@TempDir Path tmp)
            throws Exception {
        Path flfFile = tmp.resolve("external.flf");
        Files.writeString(flfFile, buildMinimalFlf(1));
        setField(mojo, "parmFontFile", flfFile.toFile());
        setField(mojo, "parmFont", "banner"); // non-default, non-blank -> triggers the precedence note

        FigletFont font = mojo.resolveFont();

        assertThat(font.getName()).isEqualTo("external");
        assertThat(log.debugContains("takes precedence")).isTrue();
    }

    @Test
    void resolveFont_fontFileNotFound_throwsMojoExecutionException(@TempDir Path tmp) {
        Path missing = tmp.resolve("does-not-exist.flf");
        setField(mojo, "parmFontFile", missing.toFile());

        assertThatThrownBy(() -> mojo.resolveFont())
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to load FIGfont");
    }

    @Test
    void resolveFont_blankFontName_fallsBackToDefaultFont() throws Exception {
        setField(mojo, "parmFont", "   ");

        FigletFont font = mojo.resolveFont();

        assertThat(font.getName()).isEqualToIgnoringCase(AbstractFontMojo.DEFAULT_FONT);
    }

    @Test
    void resolveFont_nullFontName_fallsBackToDefaultFont() throws Exception {
        setField(mojo, "parmFont", null);

        FigletFont font = mojo.resolveFont();

        assertThat(font.getName()).isEqualToIgnoringCase(AbstractFontMojo.DEFAULT_FONT);
    }

    /**
     * Builds a minimal valid FLF file content string with the given height,
     * containing all 102 required glyphs (ASCII 32-126 + 7 Deutsch).
     */
    private static String buildMinimalFlf(int height) {
        StringBuilder sb = new StringBuilder();
        sb.append("flf2a$ ").append(height).append(" ").append(height)
          .append(" 2 0 0\n");
        for (int i = 0; i < 102; i++) {
            sb.append(" @\n".repeat(Math.max(0, height - 1)))
              .append(" @@\n");
        }
        return sb.toString();
    }

}
