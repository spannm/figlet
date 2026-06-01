package io.github.spannm.figlet.maven.plugin;

import static java.util.Objects.requireNonNull;

import io.github.spannm.figlet4j.FigletException;
import io.github.spannm.figlet4j.FigletFont;
import io.github.spannm.figlet4j.FigletFontRegistry;
import io.github.spannm.figlet4j.FigletRenderer;
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
 * Show font metadata (author, date from comment block):
 * <pre>
 *   mvn figlet:list-fonts -Dfiglet.metadata=true
 * </pre>
 * <p>
 * Render a sample for every font:
 * <pre>
 *   mvn figlet:list-fonts -Dfiglet.sample=true
 *   mvn figlet:list-fonts -Dfiglet.sample=true -Dfiglet.sampleText=FIGlet
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
     * When {@code true}, loads every font and prints the full metadata comment block.
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

    int getWidth() {
        return parmWidth;
    }

    @Override
    protected void executeImpl() {
        NavigableSet<String> fontNames = FigletFontRegistry.listAllFonts();

        if (fontNames.isEmpty()) {
            getLog().warn("No fonts found. Ensure that figlet4j is on the classpath.");
            return;
        }

        getLog().debug("Listing " + fontNames.size() + " font(s)"
            + (parmMetadata ? ", with metadata" : "")
            + (parmPrintSample ? ", with samples (width=" + parmWidth + ")" : ""));

        String header = "Available fonts (" + fontNames.size() + " total):";

        getLog().info("");
        getLog().info(header);
        getLog().info("=".repeat(header.length()));

        String sampleText = parmSampleText != null && !parmSampleText.isBlank()
            ? parmSampleText
            : randomSampleText();

        int counter      = 0;
        int failureCount = 0;
        for (String name : fontNames) {
            counter++;
            boolean ok = true;
            if (parmMetadata) {
                ok = printMetadata(counter, name);
            } else {
                getLog().info(String.format("  %3s. %s", counter, name));
            }

            if (parmPrintSample) {
                ok &= printSample(name, sampleText);
            }

            if (!ok) {
                failureCount++;
            }
        }

        getLog().info("Use -D" + FIGLET_METADATA + "=true for font metadata details.");
        getLog().info("Use -D" + FIGLET_SAMPLE + "=true to render a sample for each font.");
        getLog().info("Configure the font in your pom.xml: <font>font-name</font>");
        getLog().info("");

        if (failureCount > 0) {
            getLog().warn(failureCount + " of " + fontNames.size()
                + " font(s) could not be fully processed — see warnings above for details.");
        }
    }

    private boolean printMetadata(int counter, String name) {
        requireNonNull(name, "name");

        try {
            FigletFont font = FigletFontRegistry.loadFont(name);
            List<String> commentLines = font.getComment();

            if (commentLines.isEmpty()) {
                getLog().info(String.format("  %3s. %-20s", counter, name));
                return true;
            }

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
            return true;

        } catch (Exception ex) {
            getLog().warn(String.format("  %-25s  [Could not load metadata: %s]", name, ex.getMessage()));
            return false;
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
     * Picks a varied, always-renderable fallback sample text for when
     * {@code figlet.sampleText} is not set: the current weekday, the current month,
     * the default locale's language or country (in English, to stay within the plain
     * ASCII range every FIGfont supports), the current year, or a time-of-day greeting.
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
