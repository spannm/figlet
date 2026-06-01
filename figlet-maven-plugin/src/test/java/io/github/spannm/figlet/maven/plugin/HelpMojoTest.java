package io.github.spannm.figlet.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unit tests for {@link HelpMojo}.
 * <p>
 * HelpMojo reads a static resource ({@code /figlet-help.txt}) and forwards
 * every line to {@code getLog().info()}. Tests verify that the resource is
 * present and that its content reaches the log without truncation.
 */
@SuppressWarnings("checkstyle:MethodName")
final class HelpMojoTest extends AbstractFigletMojoTestBase {

    private HelpMojo     mojo;
    private CapturingLog log;

    @BeforeEach
    void setUp() {
        mojo = new HelpMojo();
        log  = installCapturingLog(mojo);
    }

    @Test
    void execute_producesOutput() throws Exception {
        mojo.execute();

        assertThat(log.infoMessages)
            .as("Expected at least one info line from the help resource")
            .isNotEmpty();
    }

    @Test
    void execute_resourceIsPresent() throws Exception {
        // If the resource were missing, HelpMojo logs an error and returns early.
        mojo.execute();

        assertThat(log.errorContains("not found"))
            .as("Help resource /figlet-help.txt must be on the classpath")
            .isFalse();
    }

    @Test
    void execute_outputContainsPluginReference() throws Exception {
        mojo.execute();

        // the help text is expected to mention the plugin or its goals at minimum
        assertThat(log.infoContains("figlet"))
            .as("Help output should mention 'figlet'.\nActual info:\n" + log.infoAsString())
            .isTrue();
    }

    @Test
    void execute_surroundingBlankLines() throws Exception {
        mojo.execute();

        // HelpMojo emits getLog().info("") before and after content
        assertThat(log.infoMessages)
            .filteredOn(String::isEmpty)
            .as("Expected at least 2 blank framing lines")
            .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void execute_resourceMissing_logsErrorAndReturnsEarly() throws Exception {
        URL url = HelpMojo.class.getResource("/figlet-help.txt");
        assumeTrue(url != null && "file".equals(url.getProtocol()),
            "requires the help resource to be an exploded file on disk");

        Path original = Paths.get(url.toURI());
        Path movedAway = original.resolveSibling("figlet-help.txt.movedForTest");
        Files.move(original, movedAway);
        try {
            mojo.execute();

            assertThat(log.errorContains("not found")).isTrue();
            assertThat(log.infoMessages).isEmpty();
        } finally {
            Files.move(movedAway, original);
        }
    }

    @Test
    void skip_producesNoHelpOutput() throws Exception {
        setField(mojo, "parmSkip", true);
        mojo.execute();

        // The skip message itself contains "figlet", so infoContains("figlet") would
        // be a false positive. Instead verify that only the single skip notification
        // was logged — the help resource produces many lines, so > 1 means the
        // resource was read despite skip=true.
        assertThat(log.infoMessages)
            .as("Skipped HelpMojo must log exactly the skip notification, got: " + log.infoMessages)
            .hasSize(1);
        assertThat(log.infoContains("figlet.skip=true")).isTrue();
    }
}
