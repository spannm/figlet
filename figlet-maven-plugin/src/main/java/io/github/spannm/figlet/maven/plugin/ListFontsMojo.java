package io.github.spannm.figlet.maven.plugin;

import static java.util.Objects.requireNonNull;

import io.github.spannm.figlet4j.FigletException;
import io.github.spannm.figlet4j.FigletFont;
import io.github.spannm.figlet4j.FigletFontRegistry;
import io.github.spannm.figlet4j.FigletRenderer;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Lists all available FIGfonts and TOIlet fonts (alphabetically) with their metadata.
 * <p>
 * Run standalone (not bound to a lifecycle phase by default):
 * <pre>
 *   mvn figlet:list-fonts
 * </pre>
 * <p>
 * Show font metadata (author, date from comment block, supported characters):
 * <pre>
 *   mvn figlet:list-fonts -Dfiglet.metadata=true
 * </pre>
 * <p>
 * Render a sample for every font:
 * <pre>
 *   mvn figlet:list-fonts -Dfiglet.sample=true
 *   mvn figlet:list-fonts -Dfiglet.sample=true -Dfiglet.sampleText=FIGlet
 * </pre>
 * <p>
 * List only the fonts that can render a text completely:
 * <pre>
 *   mvn figlet:list-fonts -Dfiglet.strict=true -Dfiglet.sampleText=Gr&ouml;&szlig;e
 * </pre>
 *
 * @author Markus Spann
 * @since 1.0.0
 */
@Mojo(
    name            = "list-fonts",
    requiresProject = false,
    threadSafe      = true
)
public class ListFontsMojo extends AbstractFigletMojo {

    private static final String FIGLET_METADATA    = "figlet.metadata";
    private static final String FIGLET_SAMPLE      = "figlet.sample";
    private static final String FIGLET_SAMPLE_TEXT = "figlet.sampleText";
    private static final String FIGLET_STRICT      = "figlet.strict";

    /**
     * Maximum sample rendering width in characters, used only when
     * {@code figlet.sample=true}. Long sample lines are wrapped at word boundaries.
     * <p>
     * Unrelated to any single font, since this goal lists every font — hence a
     * dedicated local parameter instead of one inherited alongside {@code font}
     * / {@code fontFile} (which would not apply here, as no single font is
     * configured for this goal).
     */
    @Parameter(property = "figlet.width", defaultValue = "72", alias = "width")
    private int                 parmWidth;

    /**
     * When {@code true}, loads every font and prints the full metadata comment block
     * and the characters the font can render.
     * This is slower but reveals author and date information embedded in each font.
     */
    @Parameter(property = FIGLET_METADATA, defaultValue = "false", alias = "metadata")
    private boolean             parmMetadata;

    /**
     * When {@code true}, renders a short ASCII-art sample for every listed font.
     * The sample text can be customised via {@code figlet.sampleText}.
     * This option is independent of {@code figlet.metadata}.
     */
    @Parameter(property = FIGLET_SAMPLE, defaultValue = "false", alias = "printSample")
    private boolean             parmPrintSample;

    /**
     * The text to render as a sample when {@code figlet.sample=true}.
     * When left unset (or blank), a random always-renderable fallback is picked for
     * each run: the current weekday, the current month, the language or country of
     * the default locale, the current year, or a time-of-day greeting.
     */
    @Parameter(property = FIGLET_SAMPLE_TEXT, alias = "sampleText")
    private String              parmSampleText;

    /**
     * When {@code true}, lists only the fonts that can render {@code figlet.sampleText}
     * completely, which must then be set. When {@code false} (the default), all fonts
     * are listed, and a sample that misses characters is followed by a note naming them.
     *
     * @since 1.2.0
     */
    @Parameter(property = FIGLET_STRICT, defaultValue = "false", alias = "strict")
    private boolean             parmStrict;

    int getWidth() {
        return parmWidth;
    }

    @Override
    protected void executeImpl() throws MojoFailureException {
        boolean hasSampleText = parmSampleText != null && !parmSampleText.isBlank();
        if (parmStrict && !hasSampleText) {
            throw new MojoFailureException(String.format(
                "%s=true lists the fonts that can render a text, set it with -D%s=...", FIGLET_STRICT, FIGLET_SAMPLE_TEXT));
        }

        NavigableSet<String> allFonts = FigletFontRegistry.listAllFonts();
        if (allFonts.isEmpty()) {
            getLog().warn("No fonts found. Ensure that figlet4j is on the classpath.");
            return;
        }

        NavigableSet<String> fontNames = parmStrict
            ? allFonts.stream().filter(name -> canRender(name, parmSampleText)).collect(Collectors.toCollection(TreeSet::new))
            : allFonts;

        getLog().debug(String.format("Listing %d font(s)%s%s", fontNames.size(),
            parmMetadata ? ", with metadata" : "",
            parmPrintSample ? String.format(", with samples (width=%d)", parmWidth) : ""));

        String header = parmStrict
            ? String.format("Fonts that can render \"%s\" (%d of %d):", parmSampleText, fontNames.size(), allFonts.size())
            : String.format("Available fonts (%d total):", fontNames.size());

        getLog().info("");
        getLog().info(header);
        getLog().info("=".repeat(header.length()));

        String sampleText = hasSampleText ? parmSampleText : randomSampleText();

        int counter      = 0;
        int failureCount = 0;
        for (String name : fontNames) {
            counter++;
            boolean ok = true;
            List<String> variants = FigletFontRegistry.listSpellingVariants(name);
            if (parmMetadata) {
                ok = printMetadata(counter, name);
                if (!variants.isEmpty()) {
                    getLog().info(String.format("  %-25s  %s", "", alsoHint(variants)));
                }
            } else if (variants.isEmpty()) {
                getLog().info(String.format("  %3s. %s", counter, name));
            } else {
                getLog().info(String.format("  %3s. %s %s", counter, name, alsoHint(variants)));
            }

            if (parmPrintSample) {
                ok &= printSample(name, sampleText);
            }

            if (!ok) {
                failureCount++;
            }
        }

        getLog().info(String.format("Use -D%s=true for font metadata details.", FIGLET_METADATA));
        getLog().info(String.format("Use -D%s=true to render a sample for each font.", FIGLET_SAMPLE));
        getLog().info("Configure the font in your pom.xml: <font>font-name</font>");
        getLog().info("");

        if (failureCount > 0) {
            getLog().warn(String.format("%d of %d font(s) could not be fully processed — see warnings above for details.",
                failureCount, fontNames.size()));
        }
    }

    /**
     * Formats the hint on other spellings of a font name, e.g. {@code (also: Banner)}.
     *
     * @param variants the other spellings, not empty
     * @return the hint text
     */
    private static String alsoHint(List<String> variants) {
        return String.format("(also: %s)", String.join(", ", variants));
    }

    private boolean printMetadata(int counter, String name) {
        requireNonNull(name, "name");

        try {
            FigletFont font = FigletFontRegistry.loadFont(name);
            List<String> commentLines = font.getComment();

            // find the real end by skipping trailing empty or whitespace-only lines
            int lastNonEmptyIndex = commentLines.size() - 1;
            while (lastNonEmptyIndex >= 0 && commentLines.get(lastNonEmptyIndex).trim().isEmpty()) {
                lastNonEmptyIndex--;
            }

            int effectiveSize = lastNonEmptyIndex + 1;
            int maxLines      = Math.min(effectiveSize, 5);

            if (maxLines == 0) {
                getLog().info(String.format("  %3s. %-20s", counter, name));
            } else {
                // first comment line alongside the font name
                getLog().info(String.format("  %3s. %-20s  %s", counter, name, commentLines.get(0)));

                // subsequent comment lines, indented
                for (int i = 1; i < maxLines; i++) {
                    getLog().info(String.format("  %-25s  %s", "", commentLines.get(i)));
                }

                if (effectiveSize > 5) {
                    getLog().info(String.format("  %-25s  %s", "", "[..]"));
                }
            }
            getLog().info(String.format("  %-25s  Characters: %s", "", font.getCharacterSummary()));
            return true;

        } catch (Exception ex) {
            getLog().warn(String.format("  %-25s  [Could not load metadata: %s]", name, ex.getMessage()));
            return false;
        }
    }

    /**
     * Returns whether a font can render a text completely. A font that cannot be
     * loaded is kept, so that the failure is reported when it is listed.
     *
     * @param name font name
     * @param text the text to render
     * @return {@code true} if the font supports every character of {@code text} or fails to load
     */
    private static boolean canRender(String name, String text) {
        try {
            return FigletFontRegistry.loadFont(name).findUnsupportedCodepoints(text).isEmpty();
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private boolean printSample(String name, String text) {
        try {
            FigletFont font = FigletFontRegistry.loadFont(name);
            FigletRenderer renderer = new FigletRenderer(font).withWidth(getWidth()).setStrict(false);
            String banner = renderer.render(text);

            for (String line : banner.split("\n", -1)) {
                getLog().info("  " + line);
            }
            Set<Integer> unsupported = font.findUnsupportedCodepoints(text);
            if (!unsupported.isEmpty()) {
                getLog().info(String.format("  [cannot render: %s]", describeCodepoints(unsupported)));
            }
            return true;

        } catch (FigletException ex) {
            getLog().warn(String.format("  %-25s  [Could not render sample: %s]", name, ex.getMessage()));
            return false;
        } catch (Exception ex) {
            getLog().warn(String.format("  %-25s  [Could not load font: %s]", name, ex.getMessage()));
            return false;
        }
    }

    /**
     * Picks a varied fallback sample text for when {@code figlet.sampleText} is not
     * set: the current weekday, the current month, the default locale's language or
     * country (in English, to stay mostly within plain ASCII), the current year, or a
     * time-of-day greeting. Some fonts lack digits or other characters of these; their
     * samples are followed by a note naming the missing characters.
     *
     * @return a randomly chosen, non-blank sample text
     */
    static String randomSampleText() {
        return randomSampleText(Clock.system(ZoneId.systemDefault()), Locale.getDefault());
    }

    /**
     * Package-private overload for deterministic unit testing.
     */
    static String randomSampleText(Clock clock, Locale locale) {
        LocalDate today = LocalDate.now(clock);

        List<String> candidates = Stream.of(
            today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            locale.getDisplayLanguage(Locale.ENGLISH),
            locale.getDisplayCountry(Locale.ENGLISH),
            String.valueOf(today.getYear()),
            timeOfDayGreeting(clock))
            .filter(Objects::nonNull)
            .filter(Predicate.not(String::isBlank))
            .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return "FIGlet";
        }

        int index = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(index);
    }

    /**
     * Returns a coarse time-of-day greeting ({@code Morning}/{@code Afternoon}/
     * {@code Evening}/{@code Night}) based on the current wall-clock hour.
     *
     * @return one of the four greeting words; never blank
     */
    private static String timeOfDayGreeting(Clock clock) {
        int hour = LocalTime.now(clock).getHour();
        if (hour < 6) {
            return "Night";
        } else if (hour < 12) {
            return "Morning";
        } else if (hour < 18) {
            return "Afternoon";
        } else if (hour < 22) {
            return "Evening";
        }
        return "Night";
    }

}
