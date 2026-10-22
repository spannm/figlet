package io.github.spannm.figlet.maven.plugin;

import io.github.spannm.figlet4j.FigletException;
import io.github.spannm.figlet4j.FigletFont;
import io.github.spannm.figlet4j.FigletFontRegistry;
import io.github.spannm.figlet4j.FigletMarkupNode;
import io.github.spannm.figlet4j.FigletMarkupParser;
import io.github.spannm.figlet4j.FigletMarkupRenderer;
import io.github.spannm.figlet4j.FigletRenderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders text as ASCII art and prints it to the build log during the Maven lifecycle,
 * or writes it to a file such as the {@code banner.txt} of a Spring Boot application.
 * <p>
 * {@code content} may contain Maven property placeholders in the form
 * {@code ${property.name}} (e.g. {@code ${project.name}}) and, optionally, the
 * figlet4j markup DSL to combine multiple fonts with literal, unrendered text
 * in a single banner — see {@link io.github.spannm.figlet4j.FigletMarkupParser}
 * for the exact grammar ({@code <figletFont name="...">...</figletFont>} and
 * {@code <lineBreak/>}). A {@code <figletFont>} tag without a {@code name}
 * attribute uses {@code font}/{@code fontFile}. If {@code content} contains no {@code <figletFont>}
 * tag at all, the entire (property-resolved) content is rendered as a single
 * banner using {@code font}/{@code fontFile} — exactly as if it had been
 * wrapped in one {@code <figletFont>} tag referencing that font.
 *
 * <h2>Minimal configuration</h2>
 * An {@code <execution>} is required to bind the goal into the build — a plugin
 * merely listed in {@code <build><plugins>} without one is never invoked
 * automatically. No {@code <phase>} is needed inside it, though: the goal
 * defaults to the {@code validate} phase (see {@link LifecyclePhase#VALIDATE}).
 * To avoid repeating the {@code <execution>} block in every module, declare it
 * once in a shared parent POM instead.
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.spannm</groupId>
 *   <artifactId>figlet-maven-plugin</artifactId>
 *   <executions>
 *     <execution>
 *       <goals><goal>render</goal></goals>
 *       <configuration>
 *         <content>${project.name} ${project.version}</content>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * <h2>Mixing fonts and plain text</h2>
 * <pre>{@code
 * <configuration>
 *   <content><![CDATA[
 *     <figletFont name="standard">${project.name}</figletFont><lineBreak/>
 *     <figletFont name="small">${project.version}</figletFont><lineBreak/>
 *     built by ${user.name}
 *   ]]></content>
 * </configuration>
 * }</pre>
 *
 * <h2>Multi-module builds</h2>
 * By default, this goal only executes for the execution-root project (the
 * module Maven was invoked on) and is a no-op for every other module in the
 * reactor, even if bound in a child module's own {@code pom.xml}. This
 * prevents the same banner from being printed once per module when the
 * execution is inherited from a shared parent POM.
 * <p>
 * Some use cases do want a banner per module (e.g. a distinct name per
 * artifact). Set {@code executionRootOnly} to {@code false} — typically
 * overridden in a specific module's own {@code <configuration>}, not
 * globally in the parent — to render there too.
 *
 * <h2>Writing to a file</h2>
 * With {@code outputFile} set, the banner is written to that file instead of
 * the build log, e.g. as Spring Boot banner that needs no runtime dependency:
 * <pre>{@code
 * <configuration>
 *   <content>${project.name}</content>
 *   <outputFile>${project.build.outputDirectory}/banner.txt</outputFile>
 * </configuration>
 * }</pre>
 * In a multi-module build, configure this in the module that needs the file,
 * such as the Spring Boot application, not in a shared parent POM.
 * {@code executionRootOnly} does not apply then, as that module is usually not
 * the execution root and a file per module is no duplicate in the build log.
 *
 * @since 1.0.0
 */
@Mojo(
    name            = "render",
    defaultPhase    = LifecyclePhase.VALIDATE,
    threadSafe      = true
)
public class RenderMojo extends AbstractFontMojo {

    /** Regex matching {@code ${...}} Maven property placeholders. */
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    /**
     * Maximum output width in characters.<br>
     * Long lines are wrapped at word boundaries.
     */
    @Parameter(property = "figlet.width", defaultValue = "72", alias = "width")
    private int                  parmWidth;

    /**
     * The content to render.<br>
     * May contain Maven property placeholders such as {@code ${project.name}}
     * and/or the figlet4j markup DSL ({@code <figletFont name="...">...</figletFont>},
     * {@code <lineBreak/>}) to mix multiple fonts with literal, unrendered text.
     * See the class Javadoc for grammar and examples.<br>
     * If no {@code <figletFont>} tag is present, the whole (resolved) content
     * is rendered as one banner using {@code font}/{@code fontFile}.
     * <p>
     * Without any explicit {@code content}, the project name is rendered as an
     * ASCII art banner in the configured {@code font}/{@code fontFile}, followed
     * by the version as plain (non-figlet) text on the line below.
     */
    @Parameter(property = "figlet.content",
        defaultValue = "<lineBreak/><figletFont>${project.name}</figletFont><lineBreak/><preserveWhitespace>    v${project.version}</preserveWhitespace><lineBreak/>",
        required = true,
        alias = "content")
    private String               parmContent;

    /**
     * When {@code true} (the default), the goal fails immediately if the input
     * text contains a character not present in the selected font, or a
     * {@code ${...}} property placeholder cannot be resolved.<br>
     * When {@code false}, unsupported characters are silently replaced by
     * {@code '?'}, and unresolved placeholders are left as-is (with a warning logged).
     */
    @Parameter(property = "figlet.strict",
        defaultValue = "true",
        alias = "strict")
    private boolean              parmStrict;

    /**
     * Where to output the ASCII art banner.<br>
     * Supported values are: {@code info}, {@code debug}, {@code stdout}, {@code stderr}.
     */
    @Parameter(property = "figlet.target",
        defaultValue = "info",
        alias = "target")
    private String               parmTarget;

    /**
     * File to write the ASCII art banner to, in UTF-8, instead of the build log.<br>
     * Missing parent directories are created, an existing file is overwritten.
     *
     * @since 1.2.0
     */
    @Parameter(property = "figlet.outputFile",
        alias = "outputFile")
    private File                 parmOutputFile;

    /**
     * When {@code true} (the default), this goal only runs for the
     * execution-root project and is a no-op in every other reactor module —
     * see the class Javadoc for why. Set to {@code false} in a module that
     * should render its own banner regardless of reactor position.<br>
     * Ignored when {@code outputFile} is set.
     */
    @Parameter(property = "figlet.executionRootOnly",
        defaultValue = "true",
        alias = "executionRootOnly")
    private boolean               parmExecutionRootOnly;

    /** The current Maven project, injected automatically. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true, alias = "project")
    private MavenProject         parmProject;

    int getWidth() {
        return parmWidth;
    }

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException {
        if (parmOutputFile == null && parmExecutionRootOnly && !parmProject.isExecutionRoot()) {
            getLog().debug("Skipping figlet rendering: project is not the execution root "
                + "(set executionRootOnly=false to render in this module too)");
            return;
        }

        if (parmContent == null || parmContent.isEmpty()) {
            throw new MojoFailureException("No content to render");
        }

        List<FigletMarkupNode> nodes;
        try {
            nodes = FigletMarkupParser.parse(parmContent);
        } catch (FigletException ex) {
            throw new MojoFailureException(String.format("Failed to parse figlet content: %s", ex.getMessage()), ex);
        }

        List<FigletMarkupNode> resolvedNodes;
        try {
            resolvedNodes = resolveNodeText(nodes);
        } catch (FigletException ex) {
            throw new MojoFailureException(String.format("Failed to resolve property placeholder: %s", ex.getMessage()), ex);
        }
        boolean hasFontTag = resolvedNodes.stream().anyMatch(FigletMarkupNode::isFont);

        getLog().debug(String.format("Rendering figlet content (%d node(s), %s), width=%d, strict=%b",
            resolvedNodes.size(), hasFontTag ? "markup mode" : "single-font mode", getWidth(), parmStrict));

        String renderedText;
        try {
            if (hasFontTag) {
                // at least one <figletFont> tag is present -> resolve each referenced
                // font by name from the built-in registry, tags without a name use font/fontFile
                FigletMarkupRenderer markupRenderer = new FigletMarkupRenderer(FigletFontRegistry::loadFont)
                    .withWidth(getWidth())
                    .setStrict(parmStrict);
                if (resolvedNodes.stream().anyMatch(n -> n.isFont() && n.fontName() == null)) {
                    markupRenderer.withDefaultFont(resolveFont());
                }
                renderedText = markupRenderer.render(resolvedNodes);
            } else {
                // no markup tags at all -> treat the whole content as one banner,
                // exactly as if it had been wrapped in a single <figletFont> tag
                // referencing this goal's configured font/fontFile
                FigletFont font = resolveFont();
                String plainText = joinAsPlainText(resolvedNodes);
                renderedText = new FigletRenderer(font).withWidth(getWidth()).setStrict(parmStrict).render(plainText);
            }
        } catch (FigletException ex) {
            throw new MojoFailureException(String.format("Rendering failed: %s", ex.getMessage()), ex);
        }

        int lineCount = renderedText.split("\n", -1).length;
        if (parmOutputFile != null) {
            writeToFile(renderedText, parmOutputFile.toPath());
            getLog().info(String.format("Wrote banner (%d line(s)) to %s", lineCount, parmOutputFile));
            return;
        }
        getLog().debug(String.format("Rendered banner (%d line(s)), writing to target '%s'", lineCount, parmTarget));

        doOutput(renderedText, OutputTarget.of(parmTarget));
    }

    /**
     * Writes the banner to a file in UTF-8, terminated by a line break, creating missing parent directories.
     *
     * @param banner the rendered banner
     * @param file   the file to write
     * @throws MojoExecutionException if the file cannot be written
     */
    static void writeToFile(String banner, Path file) throws MojoExecutionException {
        String text = banner.endsWith("\n") ? banner : banner + "\n";
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new MojoExecutionException(String.format("Failed to write banner to %s: %s", file, ex.getMessage()), ex);
        }
    }

    /**
     * Resolves {@code ${...}} placeholders on each node's text content individually.
     * <p>
     * Note that Maven itself interpolates {@code ${...}} expressions in the plugin
     * configuration, including values passed via {@code -Dfiglet.content}, before
     * this goal runs and therefore before the markup is parsed. Only placeholders
     * Maven could not resolve reach this method, which is why its main job is to
     * report them according to {@code strict}. For the same reason, a property
     * value containing markup such as {@code <lineBreak/>} is interpreted as
     * markup unless it appears inside a {@code <figletFont>} tag.
     */
    List<FigletMarkupNode> resolveNodeText(List<FigletMarkupNode> nodes) {
        List<FigletMarkupNode> resolved = new ArrayList<>(nodes.size());
        for (FigletMarkupNode node : nodes) {
            String original = node.text();
            String resolvedText = resolveProperties(original);
            if (!original.equals(resolvedText)) {
                getLog().debug(String.format("Resolved '%s' to '%s'", original, resolvedText));
            }
            resolved.add(node.withText(resolvedText));
        }
        return resolved;
    }

    /**
     * Flattens a node list into a single plain-text string for the single-font
     * fallback path: {@code LINE_BREAK} nodes become a real {@code \n} (so they
     * still produce a line break even without any {@code <figletFont>} tag),
     * all other node text is appended verbatim.
     */
    static String joinAsPlainText(List<FigletMarkupNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (FigletMarkupNode node : nodes) {
            if (node.type() == FigletMarkupNode.Type.LINE_BREAK) {
                sb.append(System.lineSeparator());
            } else {
                sb.append(node.text());
            }
        }
        return sb.toString();
    }

    // the STDOUT and STDERR targets write to the console on purpose, not to the Maven log
    @SuppressWarnings("java:S106")
    void doOutput(String banner, OutputTarget target) {
        String[] lines = banner.split("\n", -1);

        for (String line : lines) {
            switch (target) {
                case STDOUT:
                    System.out.println(line);
                    break;
                case STDERR:
                    System.err.println(line);
                    break;
                case DEBUG:
                    if (getLog().isDebugEnabled()) {
                        getLog().debug(line);
                    }
                    break;
                case INFO:
                default:
                    getLog().info(line);
                    break;
            }
        }
    }

    /**
     * Resolves {@code ${property.name}} placeholders against Maven project properties
     * (project model properties + user-defined properties). Most placeholders are
     * already resolved by Maven, see {@link #resolveNodeText(List)}.
     * <p>
     * An unresolved placeholder is handled the same way {@code strict} governs
     * unsupported characters during rendering: in strict mode (the default) it
     * fails the goal immediately; otherwise it is left in the output as-is,
     * with a warning logged.
     *
     * @throws FigletException if {@code strict} is active and a placeholder cannot be resolved
     */
    String resolveProperties(String input) {
        if (input == null || !input.contains("${")) {
            return input;
        }
        Properties props = collectProperties();
        Matcher m = PROPERTY_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key   = m.group(1);
            String value = resolveProjectProperty(key);
            if (value == null) {
                value = props.getProperty(key);
            }
            if (value == null) {
                if (parmStrict) {
                    throw new FigletException(String.format("Unresolved property placeholder '${%s}'. "
                        + "Set strict=false to leave unresolved placeholders as-is instead of failing.", key));
                }
                getLog().warn(String.format("figlet-maven-plugin: unresolved property placeholder '${%s}'", key));
                value = m.group(0); // leave as-is
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves {@code project.*} property paths via reflection against the current
     * {@link MavenProject}, following each dot-separated path segment through the
     * corresponding JavaBean getter ({@code getXxx()} or {@code isXxx()}).
     * <p>
     * Unlike a fixed list of known keys, this supports any public, no-argument
     * getter reachable from {@link MavenProject} — e.g. {@code project.artifactId},
     * {@code project.build.finalName}, {@code project.organization.name}, or
     * {@code project.parent.version} — without needing to be extended for every
     * new property a user might reference.
     *
     * @param key a placeholder key such as {@code "project.name"}; keys not starting
     *            with {@code "project."} are not handled here and yield {@code null}
     * @return the resolved value as a string, or {@code null} if it could not be
     *         resolved (unknown path segment, {@code null} intermediate value, or
     *         {@code key} does not start with {@code "project."})
     */
    @SuppressWarnings("StringSplitter")
    String resolveProjectProperty(String key) {
        if (parmProject == null || key == null || !key.startsWith("project.")) {
            return null;
        }

        Object value = parmProject;
        for (String segment : key.substring("project.".length()).split("\\.")) {
            if (value == null || segment.isEmpty()) {
                return null;
            }
            try {
                value = readBeanProperty(value, segment);
            } catch (ReflectiveOperationException ex) {
                getLog().debug(String.format("figlet-maven-plugin: could not resolve '%s' via reflection (%s)",
                    key, ex.getMessage()));
                return null;
            }
        }
        return value == null ? null : value.toString();
    }

    /**
     * Reads a single JavaBean property from {@code bean} by trying, in order,
     * a no-argument {@code getXxx()} method and then a no-argument {@code isXxx()}
     * method (for {@code boolean} properties).
     *
     * @param bean         the object to read the property from; must not be {@code null}
     * @param propertyName the lower-camel-case property name, e.g. {@code "artifactId"}
     * @return the value returned by the getter; may be {@code null}
     * @throws ReflectiveOperationException if neither getter exists or invocation fails
     */
    static Object readBeanProperty(Object bean, String propertyName) throws ReflectiveOperationException {
        String capitalized = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method method = bean.getClass().getMethod(prefix + capitalized);
                return method.invoke(bean);
            } catch (NoSuchMethodException ignored) {
                // try the next prefix
            }
        }
        throw new NoSuchMethodException(String.format("%s.%s", bean.getClass().getName(), propertyName));
    }

    Properties collectProperties() {
        Properties props = new Properties();
        if (parmProject != null) {
            props.putAll(parmProject.getModel().getProperties());
            props.putAll(parmProject.getProperties());
        }
        return props;
    }

    /**
     * Target destinations for the rendered ASCII art.
     */
    enum OutputTarget {
        INFO,
        DEBUG,
        STDOUT,
        STDERR;

        public static OutputTarget of(String value) {
            if (value == null || value.isBlank()) {
                return INFO;
            }
            try {
                return OutputTarget.valueOf(value.toUpperCase(Locale.ROOT).trim());
            } catch (IllegalArgumentException ex) {
                return INFO;
            }
        }
    }

}
