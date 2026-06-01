package io.github.spannm.figlet.maven.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Properties;

/**
 * Unit tests for {@link RenderMojo}.
 * <p>
 * A minimal {@link MavenProject} stub is assembled via the public API so
 * that property-placeholder resolution can be exercised without a real Maven build.
 */
@SuppressWarnings("checkstyle:MethodName")
final class RenderMojoTest extends AbstractFigletMojoTestBase {

    private RenderMojo   mojo;
    private CapturingLog log;

    @BeforeEach
    void setUp() {
        mojo = new RenderMojo();
        log  = installCapturingLog(mojo);
        setField(mojo, "parmFont", "standard");
        setField(mojo, "parmWidth", 200);
        setField(mojo, "parmStrict", false);
        setField(mojo, "parmProject", createMinimalProject("test-artifact", "test-name", "0.8.15"));
    }

    @Test
    void execute_rendersPlainText() throws Exception {
        setField(mojo, "parmContent", "Hi");
        mojo.execute();

        assertThat(log.infoMessages)
            .as("Expected ASCII art output in the log")
            .isNotEmpty();
        // ASCII art for "Hi" must contain at least one non-blank line
        assertThat(log.infoMessages)
            .as("Expected at least one non-blank rendered line")
            .anyMatch(s -> !s.isBlank());
    }

    @Test
    void execute_multiLineViaLineBreakTag() throws Exception {
        setField(mojo, "parmContent", "Hello<lineBreak/>World");
        mojo.execute();

        // Two words rendered sequentially → more output lines than a single word
        assertThat(log.infoMessages)
            .as("Expected multi-line output for two-row banner")
            .hasSizeGreaterThan(5);
    }

    @Test
    void execute_singleFigletFontTag_shouldRenderLikePlainContent() throws Exception {
        setField(mojo, "parmContent", "<figletFont name=\"standard\">Hi</figletFont>");
        mojo.execute();

        assertThat(log.infoMessages).anyMatch(s -> !s.isBlank());
        assertThat(log.debugContains("markup mode")).isTrue();
    }

    @Test
    void execute_noFigletFontTag_shouldUseSingleFontModeAndConfiguredFont() throws Exception {
        setField(mojo, "parmContent", "Hi");
        mojo.execute();

        assertThat(log.debugContains("single-font mode")).isTrue();
    }

    @Test
    void execute_figletFontPlusPlainText_shouldCombineBoth() throws Exception {
        setField(mojo, "parmContent",
            "<figletFont name=\"standard\">${project.name}</figletFont><lineBreak/>caption text");
        mojo.execute();

        assertThat(log.infoMessages).anyMatch(s -> !s.isBlank());
        // the literal caption must appear verbatim (unrendered) among the info lines
        assertThat(log.infoMessages).anyMatch(s -> s.contains("caption text"));
    }

    @Test
    void execute_placeholderInsideFigletFontTag_shouldBeResolvedBeforeRendering() throws Exception {
        setField(mojo, "parmContent", "<figletFont name=\"standard\">${project.version}</figletFont>");
        mojo.execute();

        assertThat(log.debugContains("Resolved '${project.version}' to '0.8.15'")).isTrue();
    }

    @Test
    void execute_unknownFontInMarkup_shouldThrowMojoFailureException() {
        setField(mojo, "parmContent", "<figletFont name=\"invalid-font-name-xyz\">Hi</figletFont>");

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("invalid-font-name-xyz");
    }

    @Test
    void execute_malformedMarkup_shouldThrowMojoFailureException() {
        setField(mojo, "parmContent", "<figletFont name=\"standard\">unclosed tag");

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("Failed to parse figlet content");
    }

    @Test
    void execute_resolvesProjectName() throws Exception {
        setField(mojo, "parmContent", "${project.name}");
        mojo.execute();

        // If resolution worked, "test-name" was rendered — at least one
        // info line must be non-blank (the ASCII art itself).
        assertThat(log.infoMessages)
            .as("Expected rendered output for resolved project.name")
            .anyMatch(s -> !s.isBlank());
    }

    @Test
    void execute_resolvesProjectVersion() throws Exception {
        setField(mojo, "parmContent", "${project.version}");
        mojo.execute();

        assertThat(log.infoMessages).isNotEmpty();
    }

    @Test
    void execute_resolvesCustomMavenProperty() throws Exception {
        MavenProject project = createMinimalProject("art", "art", "2.0");
        Properties   extra   = new Properties();
        extra.setProperty("my.label", "OK");
        project.getModel().getProperties().putAll(extra);
        setField(mojo, "parmProject", project);

        setField(mojo, "parmContent", "${my.label}");
        mojo.execute();

        assertThat(log.infoMessages)
            .as("Expected rendered output for custom property")
            .isNotEmpty();
    }

    @Test
    void execute_strictFalse_logsWarningForUnresolvedPlaceholder() throws Exception {
        setField(mojo, "parmStrict", false);
        setField(mojo, "parmContent", "${no.such.property}");
        mojo.execute();

        assertThat(log.warnContains("no.such.property"))
            .as("Expected warning for unresolved placeholder.\nWarnings: " + log.warnMessages)
            .isTrue();
    }

    @Test
    void execute_strictTrue_throwsForUnresolvedPlaceholder() {
        setField(mojo, "parmStrict", true);
        setField(mojo, "parmContent", "${no.such.property}");

        assertThatThrownBy(() -> mojo.execute())
            .as("Expected MojoFailureException for unresolved placeholder in strict mode")
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("no.such.property");
    }

    @Test
    void execute_strictFalse_doesNotThrowForUnsupportedChar() {
        setField(mojo, "parmStrict", false);
        setField(mojo, "parmContent", "café");  // é may not be in all fonts
        // must not throw
        assertThatCode(() -> mojo.execute()).doesNotThrowAnyException();
    }

    @Test
    void execute_strictTrue_throwsForUnsupportedChar() {
        setField(mojo, "parmStrict", true);
        setField(mojo, "parmContent", "\u4e2d"); // CJK char — not in standard ASCII font
        setField(mojo, "parmFont", "standard");

        assertThatThrownBy(() -> mojo.execute())
            .as("Expected MojoFailureException for unsupported character in strict mode")
            .isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_notExecutionRoot_shouldSkipRendering() throws Exception {
        MavenProject project = (MavenProject) getField(mojo, "parmProject");
        project.setExecutionRoot(false);
        setField(mojo, "parmContent", "Hello");

        mojo.execute();

        assertThat(log.infoMessages).isEmpty();
        assertThat(log.debugContains("Skipping figlet rendering")).isTrue();
    }

    @Test
    void execute_invalidFont_shouldThrowMojoExecutionException() {
        setField(mojo, "parmFont", "invalid-font-name-xyz");
        setField(mojo, "parmContent", "Hello");

        assertThatThrownBy(() -> mojo.execute())
            .isInstanceOf(org.apache.maven.plugin.MojoExecutionException.class)
            .hasMessageContaining("Failed to load FIGfont");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Just plain text", ""})
    void resolveProperties_noPlaceholders_shouldReturnInputAsIs(String input) {
        assertThat(mojo.resolveProperties(input)).isEqualTo(input);
    }

    @Test
    void resolveProperties_nullInput_shouldReturnNull() {
        assertThat(mojo.resolveProperties(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "project.artifactId, test-artifact",
        "project.description, test-description",
        "project.groupId, io.github.spannm",
        "project.inceptionYear, 2026",
        "project.name, test-name",
        "project.url, https://github.com",
        "project.version, 0.8.15",
        "unknown.property, null"
    })
    void resolveProjectProperty_supportedKeys_shouldReturnExpectedValue(String key, String expectedValue) {
        MavenProject project = (MavenProject) getField(mojo, "parmProject");
        project.setDescription("test-description");
        project.setInceptionYear("2026");
        project.setUrl("https://github.com");

        String actual = mojo.resolveProjectProperty(key);

        if ("null".equals(expectedValue)) {
            assertThat(actual).isNull();
        } else {
            assertThat(actual).isEqualTo(expectedValue);
        }
    }

    @Test
    void resolveProjectProperty_nullProject_shouldReturnNull() {
        setField(mojo, "parmProject", null);
        assertThat(mojo.resolveProjectProperty("project.name")).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "project..artifactId",       // empty path segment between the two dots
        "project.parent.artifactId"  // a standalone MavenProject has no parent -> null intermediate value
    })
    void resolveProjectProperty_unresolvableKey_shouldReturnNull(String key) {
        assertThat(mojo.resolveProjectProperty(key)).isNull();
    }

    @Test
    void resolveProjectProperty_unknownBeanProperty_shouldReturnNullAndLogDebug() {
        String result = mojo.resolveProjectProperty("project.thisPropertyDoesNotExist");

        assertThat(result).isNull();
        assertThat(log.debugContains("could not resolve")).isTrue();
    }

    @Test
    void readBeanProperty_unknownProperty_shouldThrowNoSuchMethodException() {
        MavenProject project = (MavenProject) getField(mojo, "parmProject");

        assertThatThrownBy(() -> RenderMojo.readBeanProperty(project, "thisPropertyDoesNotExist"))
            .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void collectProperties_nullProject_shouldReturnEmptyProperties() {
        setField(mojo, "parmProject", null);
        assertThat(mojo.collectProperties()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "info, INFO",
        "DEBUG, DEBUG",
        "  stdout  , STDOUT",
        "stderr, STDERR",
        "invalidTarget, INFO",
        ", INFO",
        "' ', INFO"
    })
    void outputTargetOf_variousInputs_shouldResolveToExpectedEnum(String input, RenderMojo.OutputTarget expected) {
        assertThat(RenderMojo.OutputTarget.of(input)).isEqualTo(expected);
    }

    @Test
    void doOutput_allTargets_shouldRouteCorrectly() {
        mojo.doOutput("InfoLine", RenderMojo.OutputTarget.INFO);
        assertThat(log.infoMessages).contains("InfoLine");

        mojo.doOutput("DebugLine", RenderMojo.OutputTarget.DEBUG);
        assertThat(log.debugMessages).contains("DebugLine");

        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outContent = new java.io.ByteArrayOutputStream();
        ByteArrayOutputStream errContent = new java.io.ByteArrayOutputStream();

        try {
            System.setOut(new java.io.PrintStream(outContent));
            System.setErr(new java.io.PrintStream(errContent));

            mojo.doOutput("StdoutLine", RenderMojo.OutputTarget.STDOUT);
            mojo.doOutput("StderrLine", RenderMojo.OutputTarget.STDERR);

            assertThat(outContent.toString(UTF_8)).contains("StdoutLine");
            assertThat(errContent.toString(UTF_8)).contains("StderrLine");
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void doOutput_debugTarget_debugDisabled_doesNotLog() {
        log.setDebugEnabled(false);

        mojo.doOutput("SuppressedDebugLine", RenderMojo.OutputTarget.DEBUG);

        assertThat(log.debugMessages).doesNotContain("SuppressedDebugLine");
    }

    @Test
    void execute_emptyContent_throwsMojoFailureException() {
        setField(mojo, "parmContent", "");

        assertThatThrownBy(() -> mojo.execute())
            .as("Expected MojoFailureException for empty content")
            .isInstanceOf(MojoFailureException.class);
    }

    @Test
    void execute_nullContent_throwsMojoFailureException() {
        setField(mojo, "parmContent", null);

        assertThatThrownBy(() -> mojo.execute())
            .as("Expected MojoFailureException for null content")
            .isInstanceOf(MojoFailureException.class)
            .hasMessageContaining("No content to render");
    }

    @Test
    void skip_producesNoRenderedOutput() throws Exception {
        setField(mojo, "parmSkip", true);
        setField(mojo, "parmContent", "Hello");
        mojo.execute();

        assertThat(log.infoMessages)
            .as("Skipped mojo must not produce ASCII art")
            .noneMatch(s -> s.contains("_") || s.contains("|"));
        assertThat(log.infoContains("figlet.skip=true")).isTrue();
    }

    private static MavenProject createMinimalProject(String artifactId, String name, String version) {
        Model model = new Model();
        model.setGroupId("io.github.spannm");
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setName(name);
        model.setProperties(new Properties());
        MavenProject mavenProject = new MavenProject(model);
        mavenProject.setExecutionRoot(true);
        return mavenProject;
    }

}
