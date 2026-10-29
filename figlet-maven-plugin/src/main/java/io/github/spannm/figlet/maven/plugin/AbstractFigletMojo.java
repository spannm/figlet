package io.github.spannm.figlet.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.util.Collection;
import java.util.StringJoiner;

/**
 * Base class for all figlet-maven-plugin goals.
 * <p>
 * Deliberately holds only what applies to <em>every</em> goal — currently just
 * {@code skip}, the {@link #execute()} template method and the description of
 * unsupported characters shared by the rendering goals. Goal-specific
 * concerns (which font to load, output width, ...) live further down the
 * hierarchy in {@link AbstractFontMojo}, so that goals which don't need them
 * (e.g. {@code help}, {@code list-fonts}) don't inherit unrelated,
 * meaningless parameters. See {@link AbstractFontMojo} for goals that render
 * with a single, user-configurable font.
 */
abstract class AbstractFigletMojo extends AbstractMojo {

    /**
     * Skip execution of this goal entirely.
     * Can be set via {@code -Dfiglet.skip=true} on the command line.
     */
    @Parameter(property = "figlet.skip", defaultValue = "false", alias = "skip")
    private boolean parmSkip;

    boolean isSkip() {
        return parmSkip;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (parmSkip) {
            getLog().info(String.format("Skipping figlet goal '%s' (figlet.skip=true).", getClass().getSimpleName()));
            return;
        }

        getLog().debug(String.format("Executing figlet goal '%s'", getClass().getSimpleName()));
        executeImpl();
    }

    /** Subclasses implement their goal logic here. */
    protected abstract void executeImpl() throws MojoExecutionException, MojoFailureException;

    /**
     * Describes code points for a log message, each as the character in quotes
     * followed by its Unicode notation, e.g. {@code 'ä' (U+00E4), '€' (U+20AC)}.
     * Control characters are shown as Java escapes.
     *
     * @param codepoints the code points to describe
     * @return the comma-separated descriptions
     */
    static String describeCodepoints(Collection<Integer> codepoints) {
        StringJoiner joiner = new StringJoiner(", ");
        for (int cp : codepoints) {
            String ch = Character.isISOControl(cp) ? String.format("\\u%04X", cp) : new String(Character.toChars(cp));
            joiner.add(String.format("'%s' (U+%04X)", ch, cp));
        }
        return joiner.toString();
    }

}
