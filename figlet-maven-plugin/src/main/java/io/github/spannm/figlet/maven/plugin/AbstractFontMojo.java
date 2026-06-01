package io.github.spannm.figlet.maven.plugin;

import io.github.spannm.figlet4j.FigletException;
import io.github.spannm.figlet4j.FigletFont;
import io.github.spannm.figlet4j.FigletFontLoader;
import io.github.spannm.figlet4j.FigletFontRegistry;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.util.Optional;

/**
 * Base class for goals that render with exactly one, user-configurable FIGfont
 * (built-in by name, or an external {@code .flf} file).
 * <p>
 * Goals that operate on <em>all</em> fonts at once (e.g. {@code list-fonts}) or
 * that don't touch fonts at all (e.g. {@code help}) intentionally do
 * <strong>not</strong> extend this class, so they don't inherit {@code font} /
 * {@code fontFile} parameters that would be meaningless for them.
 *
 * @author Markus Spann
 * @since 1.0.0
 */
abstract class AbstractFontMojo extends AbstractFigletMojo {

    static final String DEFAULT_FONT = "standard";

    /**
     * Name of a built-in font to use (without the {@code .flf} extension).<br>
     * If neither {@code font} nor {@code fontFile} is specified, the default
     * font ({@code standard}) is used.
     * <p>
     * Use the {@code figlet:list-fonts} goal to see all available built-in fonts.
     */
    @Parameter(property = "figlet.font", defaultValue = DEFAULT_FONT, alias = "font")
    private String  parmFont;

    /**
     * Path to an external {@code .flf} font file on the file system.<br>
     * Takes precedence over {@code font} when both are set.
     */
    @Parameter(property = "figlet.fontFile", alias = "fontFile")
    private File    parmFontFile;

    String getFont() {
        return parmFont;
    }

    File getFontFile() {
        return parmFontFile;
    }

    /**
     * Resolves the configured font, falling back to the default ({@code standard}).
     *
     * @return loaded {@link FigletFont}
     * @throws MojoExecutionException if the font cannot be found or parsed
     */
    protected FigletFont resolveFont() throws MojoExecutionException {
        try {
            if (parmFontFile != null) {
                if (parmFont != null && !parmFont.isBlank() && !DEFAULT_FONT.equals(parmFont)) {
                    getLog().debug("Both 'font' (" + parmFont + ") and 'fontFile' (" + parmFontFile
                        + ") are configured; 'fontFile' takes precedence.");
                }
                getLog().debug("Loading external font from: " + parmFontFile);
                FigletFont font = FigletFontLoader.loadFromFile(parmFontFile.toPath());
                getLog().info("Using external font '" + font.getName() + "' from " + parmFontFile);
                return font;
            }

            String fontName = Optional.ofNullable(parmFont).filter(s -> !s.isBlank()).orElse(DEFAULT_FONT);
            getLog().debug("Loading built-in font: " + fontName);
            FigletFont font = FigletFontRegistry.loadFont(fontName);
            getLog().debug("Using built-in font '" + font.getName() + "'");
            return font;
        } catch (FigletException ex) {
            throw new MojoExecutionException("Failed to load FIGfont: " + ex.getMessage(), ex);
        }
    }

}
