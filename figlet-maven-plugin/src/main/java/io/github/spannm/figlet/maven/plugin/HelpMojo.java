package io.github.spannm.figlet.maven.plugin;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.maven.plugins.annotations.Mojo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Stream;

/**
 * Displays usage information and full credits for the figlet-maven-plugin.
 * <p>
 * This mojo reads the static help content from a resource file
 * and outputs it to the Maven log infrastructure.
 *
 * <pre>
 * mvn figlet:help
 * </pre>
 *
 * @since 1.0.0
 */
@Mojo(
    name            = "help",
    requiresProject = false,
    threadSafe      = true
)
public class HelpMojo extends AbstractFigletMojo {

    private static final String HELP_RESOURCE = "/figlet-help.txt";

    @Override
    protected void executeImpl() {
        try (InputStream is = getClass().getResourceAsStream(HELP_RESOURCE)) {
            if (is == null) {
                getLog().error("Help content resource not found: " + HELP_RESOURCE);
                return;
            }

            getLog().info("");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8));
                 Stream<String> lines = reader.lines()) {

                lines.forEach(line -> getLog().info(line));
            }
            getLog().info("");

        } catch (IOException ex) {
            getLog().error("Failed to read help content from resource", ex);
        }
    }

}
