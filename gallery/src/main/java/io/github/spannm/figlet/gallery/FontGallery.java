package io.github.spannm.figlet.gallery;

import io.github.spannm.figlet4j.FigletFont;
import io.github.spannm.figlet4j.FigletFontRegistry;
import io.github.spannm.figlet4j.FigletRenderer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates the static font gallery published on GitHub Pages: an index page
 * listing every built-in font with a sample, and one page per font with
 * samples, metadata and usage snippets.
 * <p>
 * Run it from the project root with figlet4j and both font modules on the classpath:
 * <pre>
 * java -cp figlet4j/target/classes:figlet-fonts-figletorg/target/classes:figlet-fonts-xero/target/classes \
 *     gallery/src/main/java/io/github/spannm/figlet/gallery/FontGallery.java \
 *     target/gallery 1.1.0 figlet-fonts-figletorg/target/classes/fonts
 * </pre>
 * The arguments are the output directory, the released version shown in the
 * snippets, and the font directory of the default font pack.
 *
 * @author Markus Spann
 * @since 1.2.0
 */
public class FontGallery {

    private static final String BASE_URL   = "https://spannm.github.io/figlet/";
    private static final String REPO_URL   = "https://github.com/spannm/figlet";
    private static final Path   STATIC_DIR = Path.of("gallery", "static");

    /** Output width large enough that no sample wraps. */
    private static final int          NO_WRAP_WIDTH = 10_000;
    private static final String       TITLE_FONT    = "standard";
    private static final String       TITLE_TEXT    = "figlet fonts";
    private static final String       SAMPLE_TEXT   = "Figlet4j";
    private static final List<String> CHARSET_LINES = List.of("ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "abcdefghijklm", "nopqrstuvwxyz", "0123456789");

    private static final String DEFAULT_PACK = "figlet4j (default)";
    private static final String XERO_PACK    = "figlet-fonts-xero";

    /** Plugin configuration, formatted with the version and the font name. */
    private static final String PLUGIN_SNIPPET = """
        <plugin>
          <groupId>io.github.spannm</groupId>
          <artifactId>figlet-maven-plugin</artifactId>
          <version>%s</version>
          <configuration>
            <font>%s</font>
          </configuration>
          <executions>
            <execution>
              <goals>
                <goal>render</goal>
              </goals>
            </execution>
          </executions>
        </plugin>""";

    /** Java usage, formatted with the font name and the sample text. */
    private static final String JAVA_SNIPPET = """
        FigletFont font = FigletFontRegistry.loadFont("%s");
        System.out.println(new FigletRenderer(font).render("%s"));""";

    /** Dependency on the extended font pack, formatted with the version. */
    private static final String XERO_SNIPPET = """
        <dependency>
          <groupId>io.github.spannm</groupId>
          <artifactId>figlet-fonts-xero</artifactId>
          <version>%s</version>
        </dependency>""";

    private final Path        outDir;
    private final String      version;
    private final Set<String> defaultPack;

    /**
     * Creates a gallery generator.
     *
     * @param outDir      directory the site is written to
     * @param version     released version shown in the snippets
     * @param defaultPack lowercase names of the fonts in the default font pack
     */
    FontGallery(Path outDir, String version, Set<String> defaultPack) {
        this.outDir = outDir;
        this.version = version;
        this.defaultPack = defaultPack;
    }

    /**
     * Entry point, see the class Javadoc for the arguments.
     *
     * @param args output directory, version and default font pack directory
     * @throws IOException if the site cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Usage: FontGallery <outDir> <version> <defaultFontDir>");
            System.exit(2);
        }
        new FontGallery(Path.of(args[0]), args[1], readFontNames(Path.of(args[2]))).generate();
    }

    /**
     * Returns the lowercase names of the font files in a directory.
     *
     * @param fontDir directory containing {@code .flf} and {@code .tlf} files
     * @return font names without extension
     * @throws IOException if the directory cannot be listed
     */
    static Set<String> readFontNames(Path fontDir) throws IOException {
        try (Stream<Path> files = Files.list(fontDir)) {
            return files.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".flf") || n.endsWith(".tlf"))
                .map(n -> n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        }
    }

    /**
     * Loads all fonts and writes the index page, the font pages, the sitemap and the static assets.
     *
     * @throws IOException if a file cannot be written
     */
    void generate() throws IOException {
        List<Entry> entries = loadEntries();
        Files.createDirectories(outDir.resolve("fonts"));
        copyAssets();
        write("index.html", indexPage(entries));
        for (int i = 0; i < entries.size(); i++) {
            Entry prev = i > 0 ? entries.get(i - 1) : null;
            Entry next = i < entries.size() - 1 ? entries.get(i + 1) : null;
            write("fonts/" + entries.get(i).slug() + ".html", fontPage(entries.get(i), prev, next));
        }
        write("sitemap.xml", sitemap(entries));
        write("robots.txt", String.format("""
            User-agent: *
            Allow: /
            Sitemap: %ssitemap.xml
            """, BASE_URL));
        write(".nojekyll", "");
        System.out.printf("Wrote %d font pages to %s%n", entries.size(), outDir.toAbsolutePath());
    }

    /**
     * Copies stylesheet, script and images to the assets directory of the site.
     *
     * @throws IOException if a file cannot be copied
     */
    void copyAssets() throws IOException {
        Path assets = Files.createDirectories(outDir.resolve("assets"));
        try (Stream<Path> files = Files.list(STATIC_DIR)) {
            for (Path file : files.toList()) {
                Files.copy(file, assets.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Loads every built-in font in name order, skipping and reporting fonts that fail to load.
     *
     * @return one entry per loadable font
     */
    List<Entry> loadEntries() {
        Map<String, String> builtin = FigletFontRegistry.getBuiltinFonts();
        Set<String> slugs = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        for (String name : FigletFontRegistry.listAllFonts()) {
            try {
                FigletFont font = FigletFontRegistry.loadFont(name);
                String resource = builtin.get(name);
                String type = resource.substring(resource.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                String slug = slug(name);
                for (int n = 2; !slugs.add(slug); n++) {
                    slug = slug(name) + "-" + n;
                }
                boolean inDefaultPack = defaultPack.contains(name.toLowerCase(Locale.ROOT));
                entries.add(new Entry(name, slug, type, inDefaultPack, font, FigletFontRegistry.listSpellingVariants(name)));
            } catch (RuntimeException ex) {
                System.err.printf("Skipping font %s: %s%n", name, ex);
            }
        }
        return entries;
    }

    /**
     * Renders the index page with one card per font.
     *
     * @param entries all fonts
     * @return the page HTML
     */
    String indexPage(List<Entry> entries) {
        long defaultCount = entries.stream().filter(Entry::inDefaultPack).count();
        String cards = entries.stream().map(FontGallery::card).collect(Collectors.joining("\n"));
        String body = String.format("""
            <header class="hero">
                <pre class="art hero-art" aria-hidden="true">%1$s</pre>
                <h1>FIGlet font gallery</h1>
                <p>All %2$d FIGlet and TOIlet fonts available in <a href="%3$s">figlet</a>, a pure-Java FIGlet library and Maven plugin.
                    %4$d of them come with figlet4j, the others with the optional figlet-fonts-xero module.
                    Every sample on this site was rendered by figlet4j %5$s, select a font for its character set and usage.</p>
            </header>
            <div class="controls" role="search">
                <input id="q" type="search" placeholder="Search %2$d fonts" aria-label="Search fonts" autocomplete="off">
                <select id="pack" aria-label="Font pack">
                    <option value="">All font packs</option>
                    <option value="default">%6$s</option>
                    <option value="xero">%7$s</option>
                </select>
                <select id="type" aria-label="Font type">
                    <option value="">All types</option>
                    <option value="flf">FIGfont (.flf)</option>
                    <option value="tlf">TOIlet (.tlf)</option>
                </select>
                <span id="count" aria-live="polite"></span>
            </div>
            <main class="grid">
            %8$s
            </main>
            <p id="empty" hidden>No font matches your search.</p>""",
            esc(render(FigletFontRegistry.loadFont(TITLE_FONT), TITLE_TEXT)), entries.size(), REPO_URL, defaultCount, esc(version),
            DEFAULT_PACK, XERO_PACK, cards);

        String jsonLd = String.format("""
            <script type="application/ld+json">
            {
                "@context": "https://schema.org",
                "@type": "SoftwareSourceCode",
                "name": "figlet",
                "description": "Pure-Java FIGlet library and Maven plugin for rendering text as ASCII art",
                "codeRepository": "%s",
                "programmingLanguage": "Java",
                "license": "https://www.apache.org/licenses/LICENSE-2.0",
                "version": "%s"
            }
            </script>""", REPO_URL, esc(version));

        String description = String.format("""
            Preview all %d FIGlet and TOIlet fonts that come with figlet4j, the pure-Java FIGlet library and Maven plugin. \
            Search by name, compare samples and copy the Maven or Java snippet for each font.""", entries.size());
        return page(String.format("FIGlet Font Gallery: %d ASCII Art Fonts for Java", entries.size()), description, BASE_URL, "", jsonLd, body);
    }

    /**
     * Renders the index card of a font, showing the sample text rendered in the font.
     *
     * @param e the font
     * @return the card HTML
     */
    static String card(Entry e) {
        return String.format("""
            <a class="card" href="fonts/%s.html" data-name="%s" data-pack="%s" data-type="%s">
                <pre class="art fit" aria-hidden="true">%s</pre>
                <span class="card-name">%s</span>
                <span class="card-meta">.%s, %d lines, %s</span>
            </a>""",
            e.slug(), esc(e.name().toLowerCase(Locale.ROOT)), e.inDefaultPack() ? "default" : "xero", e.type(),
            esc(render(e.font(), SAMPLE_TEXT)), esc(e.name()), e.type(), e.font().getHeight(), e.packLabel());
    }

    /**
     * Renders the page of a single font.
     *
     * @param e    the font
     * @param prev previous font in name order, or {@code null}
     * @param next next font in name order, or {@code null}
     * @return the page HTML
     */
    String fontPage(Entry e, Entry prev, Entry next) {
        FigletFont font = e.font();
        String charset = CHARSET_LINES.stream().map(s -> render(font, s)).collect(Collectors.joining("\n"));
        String body = String.format("""
            <nav class="crumbs"><a href="../">Font gallery</a> / %1$s</nav>
            <header>
                <h1>%1$s</h1>
                <p class="lead">%2$s font (.%3$s), %4$d lines high, included in %5$s.</p>
            </header>
            <pre class="art big">%6$s</pre>
            <section>
                <h2>Sample text</h2>
                <pre class="art">%7$s</pre>
            </section>
            <section>
                <h2>Character set</h2>
                <pre class="art">%8$s</pre>
            </section>
            %9$s
            %10$s
            %11$s
            %12$s""",
            esc(e.name()), e.formatLabel(), e.type(), font.getHeight(), e.packLabel(),
            esc(render(font, e.name())), esc(render(font, SAMPLE_TEXT)), esc(charset),
            details(e), usage(e), fontInfo(e), pager(prev, next));

        String description = String.format("""
            Preview of the %s FIGlet font (.%s, %d lines high) with sample text and character set. \
            Use it in Java with figlet4j or in your Maven build with figlet-maven-plugin.""", e.name(), e.type(), font.getHeight());
        return page(String.format("%s - FIGlet Font Preview", e.name()), description, BASE_URL + "fonts/" + e.slug() + ".html", "../", "", body);
    }

    /**
     * Renders the table of font properties.
     *
     * @param e the font
     * @return the section HTML
     */
    static String details(Entry e) {
        FigletFont font = e.font();
        String variants = e.variants().isEmpty() ? ""
            : String.format("<tr><th>Also spelled</th><td>%s</td></tr>", esc(String.join(", ", e.variants())));
        return String.format("""
            <section>
                <h2>Details</h2>
                <table class="facts">
                    <tr><th>Name</th><td><code>%s</code></td></tr>
                    <tr><th>Format</th><td>%s (.%s)</td></tr>
                    <tr><th>Font pack</th><td>%s</td></tr>
                    <tr><th>Height</th><td>%d lines, baseline at %d</td></tr>
                    <tr><th>Layout</th><td>%s</td></tr>
                    <tr><th>Print direction</th><td>%s</td></tr>
                    <tr><th>Glyphs</th><td>%d</td></tr>
                    %s
                </table>
            </section>""",
            esc(e.name()), e.formatLabel(), e.type(), e.packLabel(), font.getHeight(), font.getBaseline(),
            e.layoutLabel(), font.isRightToLeft() ? "right to left" : "left to right", font.getCharacters().size(), variants);
    }

    /**
     * Renders the snippets that use the font with the Maven plugin and with figlet4j.
     *
     * @param e the font
     * @return the section HTML
     */
    String usage(Entry e) {
        String xero = e.inDefaultPack() ? "" : String.format("""
            <p>This font is not part of the default font pack, so add figlet-fonts-xero next to figlet4j:</p>
            <pre class="code"><code>%s</code></pre>""", esc(String.format(XERO_SNIPPET, version)));
        return String.format("""
            <section>
                <h2>Use it</h2>
                <p>In the build log, with <a href="%1$s#installation">figlet-maven-plugin</a>, which includes all fonts:</p>
                <pre class="code"><code>%2$s</code></pre>
                <p>In Java, with <a href="%1$s#as-a-java-library-figlet4j">figlet4j</a>:</p>
                <pre class="code"><code>%3$s</code></pre>
                %4$s
            </section>""",
            REPO_URL, esc(String.format(PLUGIN_SNIPPET, version, e.name())), esc(String.format(JAVA_SNIPPET, e.name(), SAMPLE_TEXT)), xero);
    }

    /**
     * Renders the header comment of the font file, which usually names author and license.
     *
     * @param e the font
     * @return the section HTML, empty if the font has no comment
     */
    static String fontInfo(Entry e) {
        String comment = String.join("\n", e.font().getComment()).strip();
        if (comment.isEmpty()) {
            return "";
        }
        return String.format("""
            <section>
                <h2>Font info</h2>
                <p>The header comment of the font file, with its author and license where given.</p>
                <pre class="comment">%s</pre>
            </section>""", esc(comment));
    }

    /**
     * Renders the links to the previous and the next font.
     *
     * @param prev previous font, or {@code null}
     * @param next next font, or {@code null}
     * @return the navigation HTML
     */
    static String pager(Entry prev, Entry next) {
        String prevLink = prev == null ? "<span></span>" : String.format("<a href=\"%s.html\" rel=\"prev\">&larr; %s</a>", prev.slug(), esc(prev.name()));
        String nextLink = next == null ? "<span></span>" : String.format("<a href=\"%s.html\" rel=\"next\">%s &rarr;</a>", next.slug(), esc(next.name()));
        return String.format("""
            <nav class="pager">
                %s
                %s
            </nav>""", prevLink, nextLink);
    }

    /**
     * Wraps a page body into the common HTML skeleton.
     *
     * @param title       page title
     * @param description meta description
     * @param canonical   canonical URL of the page
     * @param root        relative path from the page to the site root
     * @param head        additional head elements
     * @param body        page body
     * @return the complete page
     */
    String page(String title, String description, String canonical, String root, String head, String body) {
        return String.format("""
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%1$s</title>
                <meta name="description" content="%2$s">
                <link rel="canonical" href="%3$s">
                <meta property="og:type" content="website">
                <meta property="og:title" content="%1$s">
                <meta property="og:description" content="%2$s">
                <meta property="og:url" content="%3$s">
                <meta property="og:image" content="%4$sassets/social-preview.png">
                <meta name="twitter:card" content="summary_large_image">
                <link rel="stylesheet" href="%5$sassets/style.css">
                %6$s
            </head>
            <body>
                <div class="wrap">
                    %7$s
                    <footer>
                        <p>Rendered with figlet4j %8$s, part of <a href="%9$s">figlet</a>, a pure-Java FIGlet library and Maven plugin
                            (Apache License 2.0). Fonts from <a href="https://www.figlet.org">figlet.org</a>
                            and <a href="https://github.com/xero/figlet-fonts">xero/figlet-fonts</a> retain their original licenses.</p>
                    </footer>
                </div>
                <script src="%5$sassets/gallery.js"></script>
            </body>
            </html>
            """, esc(title), esc(description), canonical, BASE_URL, root, head, body, esc(version), REPO_URL);
    }

    /**
     * Builds the sitemap listing the index and all font pages.
     *
     * @param entries all fonts
     * @return the sitemap XML
     */
    static String sitemap(List<Entry> entries) {
        String today = LocalDate.now().toString();
        String urls = entries.stream()
            .map(e -> String.format("    <url><loc>%sfonts/%s.html</loc><lastmod>%s</lastmod></url>", BASE_URL, e.slug(), today))
            .collect(Collectors.joining("\n"));
        return String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                <url><loc>%1$s</loc><lastmod>%2$s</lastmod></url>
            %3$s
            </urlset>
            """, BASE_URL, today, urls);
    }

    /**
     * Renders text in a font without wrapping, printing '?' for missing characters.
     *
     * @param font the font
     * @param text text to render
     * @return the rendered text without trailing blank lines
     */
    static String render(FigletFont font, String text) {
        return new FigletRenderer(font).withWidth(NO_WRAP_WIDTH).setStrict(false).render(text).stripTrailing();
    }

    /**
     * Turns a font name into a file name of lowercase letters, digits and dashes.
     *
     * @param name font name
     * @return the slug
     */
    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "font" : slug;
    }

    /**
     * Escapes text for HTML and drops control characters other than line feeds.
     *
     * @param s text to escape
     * @return the escaped text
     */
    static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        s.codePoints().forEach(c -> {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    if (c == '\n' || !Character.isISOControl(c)) {
                        sb.appendCodePoint(c);
                    }
                }
            }
        });
        return sb.toString();
    }

    /**
     * Writes a file below the output directory.
     *
     * @param relPath path relative to the output directory
     * @param content file content
     */
    void write(String relPath, String content) {
        try {
            Files.writeString(outDir.resolve(relPath), content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * A font as shown in the gallery.
     *
     * @param name          font name as listed by the registry
     * @param slug          file name of the font page without extension
     * @param type          file extension, {@code flf} or {@code tlf}
     * @param inDefaultPack whether the font comes with figlet4j
     * @param font          the loaded font
     * @param variants      other spellings of the name
     */
    record Entry(String name, String slug, String type, boolean inDefaultPack, FigletFont font, List<String> variants) {

        /**
         * Returns the name of the font pack providing this font.
         *
         * @return the font pack label
         */
        String packLabel() {
            return inDefaultPack ? DEFAULT_PACK : XERO_PACK;
        }

        /**
         * Returns the name of the font format.
         *
         * @return the format label
         */
        String formatLabel() {
            return "tlf".equals(type) ? "TOIlet" : "FIGlet";
        }

        /**
         * Returns how the font lays out adjacent glyphs.
         *
         * @return the layout label
         */
        String layoutLabel() {
            if (font.isFullWidth()) {
                return "full width";
            }
            return font.isSmushing() ? "smushing" : "fitting";
        }
    }
}
