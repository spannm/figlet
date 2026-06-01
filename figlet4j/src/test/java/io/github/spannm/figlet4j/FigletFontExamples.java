package io.github.spannm.figlet4j;

import static java.util.stream.Collectors.toList;

import java.util.List;

/**
 * Provides executable examples for rendering text banners using various FIGlet fonts.
 * <p>
 * This utility class demonstrates the programmatic discovery, loading, and
 * rendering capabilities of the figlet4j library.
 */
public final class FigletFontExamples {

    private FigletFontExamples() {
        throw new UnsupportedOperationException(
            "Utility class " + getClass().getSimpleName() + " cannot be instantiated");
    }

    /**
     * Main entry point for the example application.
     * <p>
     * Renders either the provided command-line arguments as a single string
     * or defaults to "Moo" if no arguments are supplied.
     *
     * @param args the command-line arguments to be rendered as a banner
     */
    public static void main(String[] args) {
        String word = args.length > 0 ? String.join(" ", args) : "figlet4j";
        printExamples(word);
    }

    /**
     * Discovers all registered FIGlet fonts and prints the rendered text
     * for each font to the standard output.
     * <p>
     * The renderer is configured with a fixed width of 100 characters
     * and strict layout rules enabled.
     *
     * @param word the text content to be rendered in all available fonts
     */
    private static void printExamples(String word) {
        List<FigletFont> fonts = FigletFontRegistry.listAllFonts().stream()
            .map(FigletFontRegistry::loadFont)
            .collect(toList());

        for (FigletFont font : fonts) {
            FigletRenderer renderer = new FigletRenderer(font).withWidth(100).setStrict(true);
            String banner = renderer.render(word);
            System.out.printf("Font: %s%n%s%n%n%n", font.getName(), banner);
        }
    }

}
