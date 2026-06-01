package io.github.spannm.figlet.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for all figlet-maven-plugin goals.
 * <p>
 * Deliberately holds only what applies to <em>every</em> goal — currently just
 * {@code skip} and the {@link #execute()} template method. Goal-specific
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
            getLog().info("Skipping figlet goal '" + getClass().getSimpleName() + "' (figlet.skip=true).");
            return;
        }

        getLog().debug("Executing figlet goal '" + getClass().getSimpleName() + "'");
        executeImpl();
    }

    /** Subclasses implement their goal logic here. */
    protected abstract void executeImpl() throws MojoExecutionException, MojoFailureException;

}
