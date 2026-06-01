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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders text as ASCII art and prints it to the build log during the Maven lifecycle.
 * <p>
 * {@code content} may contain Maven property placeholders in the form
 * {@code ${property.name}} (e.g. {@code ${project.name}}) and, optionally, the
 * figlet4j markup DSL to combine multiple fonts with literal, unrendered text
 * in a single banner — see {@link io.github.spannm.figlet4j.FigletMarkupParser}
 * for the exact grammar ({@code <figletFont name="...">...</figletFont>} and
 * {@code <lineBreak/>}). If {@code content} contains no {@code <figletFont>}
 * tag at all, the entire (property-resolved) content is rendered as a single
 * banner using {@code font}/{@code fontFile} — exactly as if it had been
 * wrapped in one {@code <figletFont>} tag referencing that font.
 *
 * <h3>Minimal configuration</h3>
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
 * <h3>Mixing fonts and plain text</h3>
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
 * <h3>Multi-module builds</h3>
 * This goal only executes for the execution-root project (the module Maven
 * was invoked on) and is a no-op for every other module in the reactor, even
 * if bound in a child module's own {@code pom.xml}. This prevents the same
 * banner from being printed once per module.
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
     */
    @Parameter(property = "figlet.content", defaultValue = "${project.name}", required = true, alias = "content")
    private String               parmContent;

    /**
     * When {@code true} (the default), the goal fails immediately if the input
     * text contains a character not present in the selected font, or a
     * {@code ${...}} property placeholder cannot be resolved.<br>
     * When {@code false}, unsupported characters are silently replaced by
     * {@code '?'}, and unresolved placeholders are left as-is (with a warning logged).
     */
    @Parameter(property = "figlet.strict", defaultValue = "true", alias = "strict")
    private boolean              parmStrict;

    /**
     * Where to output the ASCII art banner.<br>
     * Supported values are: {@code info}, {@code debug}, {@code stdout}, {@code stderr}.
     */
    @Parameter(property = "figlet.target", defaultValue = "info", alias = "target")
    private String               parmTarget;

    /** The current Maven project, injected automatically. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true, alias = "project")
    private MavenProject         parmProject;

    int getWidth() {
        return parmWidth;
    }

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException {
        if (!parmProject.isExecutionRoot()) {
            getLog().debug("Skipping figlet rendering: project is not the execution root");
            return;
        }

        if (parmContent == null || parmContent.isEmpty()) {
            throw new MojoFailureException("No content to render");
        }

        List<FigletMarkupNode> nodes;
        try {
            nodes = FigletMarkupParser.parse(parmContent);
        } catch (FigletException ex) {
            throw new MojoFailureException("Failed to parse figlet content: " + ex.getMessage(), ex);
        }

        List<FigletMarkupNode> resolvedNodes;
        try {
            resolvedNodes = resolveNodeText(nodes);
        } catch (FigletException ex) {
            throw new MojoFailureException("Failed to resolve property placeholder: " + ex.getMessage(), ex);
        }
        boolean hasFontTag = resolvedNodes.stream().anyMatch(FigletMarkupNode::isFont);

        getLog().debug("Rendering figlet content (" + resolvedNodes.size() + " node(s), "
            + (hasFontTag ? "markup mode" : "single-font mode") + "), width=" + getWidth()
            + ", strict=" + parmStrict);

        String renderedText;
        try {
            if (hasFontTag) {
                // at least one <figletFont> tag is present -> resolve each referenced
                // font by name from the built-in registry
                FigletMarkupRenderer markupRenderer = new FigletMarkupRenderer(FigletFontRegistry::loadFont)
                    .withWidth(getWidth())
                    .setStrict(parmStrict);
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
            throw new MojoFailureException("Rendering failed: " + ex.getMessage(), ex);
        }

        int lineCount = renderedText.split("\n", -1).length;
        getLog().debug("Rendered banner (" + lineCount + " line(s)), writing to target '" + parmTarget + "'");

        doOutput(renderedText, OutputTarget.of(parmTarget));
    }

    /**
     * Resolves {@code ${...}} placeholders on each node's text content individually —
     * never on the raw markup string — so that a resolved value containing
     * {@code <} or {@code >} can never be reinterpreted as markup structure.
     */
    List<FigletMarkupNode> resolveNodeText(List<FigletMarkupNode> nodes) {
        List<FigletMarkupNode> resolved = new ArrayList<>(nodes.size());
        for (FigletMarkupNode node : nodes) {
            String original = node.text();
            String resolvedText = resolveProperties(original);
            if (!original.equals(resolvedText)) {
                getLog().debug("Resolved '" + original + "' to '" + resolvedText + "'");
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
     * (project model properties + user-defined properties).
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
                    throw new FigletException("Unresolved property placeholder '${" + key + "}'. "
                        + "Set strict=false to leave unresolved placeholders as-is instead of failing.");
                }
                getLog().warn("figlet-maven-plugin: unresolved property placeholder '${" + key + "}'");
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
                getLog().debug("figlet-maven-plugin: could not resolve '" + key
                    + "' via reflection (" + ex.getMessage() + ")");
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
        throw new NoSuchMethodException(bean.getClass().getName() + "." + propertyName);
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
