package io.github.spannm.figlet4j;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableNavigableMap;
import static java.util.Collections.unmodifiableNavigableSet;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry of all FIGfonts and TOIlet fonts available to figlet4j.
 * <p>
 * Built-in fonts are discovered dynamically at class-load time by scanning
 * the {@code /fonts} directory in the classpath. This supports both packaged
 * JAR files and exploded directories (e.g., during development in an IDE).
 * <p>
 * Additional fonts can be registered at runtime via
 * {@link #registerExternal(String, Path)}.
 *
 * <h2>Name collisions</h2>
 * Font names are case-insensitive, yet several font files may share a name,
 * e.g. {@code banner.flf} and {@code Banner.flf} in different font modules. A
 * name spelled exactly like a font file resolves to that file; otherwise the
 * font discovered first wins, in classpath order. Within one directory, a
 * {@code .tlf} font is preferred over a {@code .flf} font of the same name.
 *
 * <h2>Thread safety</h2>
 * Built-in font discovery uses the <em>initialization-on-demand holder</em>
 * idiom and is therefore fully thread-safe without explicit synchronization.
 * Registered external fonts and parsed fonts are kept in concurrent maps, so
 * {@link #registerExternal} and {@link #loadFont} may be called concurrently.
 *
 * @author Markus Spann
 * @since 1.0.0
 */
public final class FigletFontRegistry {

    private static final Logger            LOGGER          = System.getLogger(FigletFontRegistry.class.getName());
    private static final String            FONTS_DIR       = "fonts";
    private static final String            EXTERNAL_PREFIX = "external:";

    /** Runtime-registered external fonts: lowercase name → file-system path */
    private static final Map<String, Path> EXTERNAL_FONTS = new ConcurrentHashMap<>();

    /** Cache of already-loaded/parsed fonts: resource path, or prefixed lowercase name of an external font → parsed font */
    private static final Map<String, FigletFont> FONT_CACHE = new ConcurrentHashMap<>();

    private FigletFontRegistry() {
        throw new UnsupportedOperationException(
            String.format("Utility class %s cannot be instantiated", getClass().getSimpleName()));
    }

    /**
     * Returns all available font names — built-in fonts plus any externally
     * registered fonts — sorted alphabetically (case-insensitive). Built-in
     * fonts whose names differ only in case are listed separately.
     *
     * @return unmodifiable sorted set of font names without extensions
     */
    public static NavigableSet<String> listAllFonts() {
        NavigableSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        all.addAll(FontHolder.EXACT.keySet());
        all.addAll(EXTERNAL_FONTS.keySet());
        return unmodifiableNavigableSet(all);
    }

    /**
     * Returns a map of all classpath-bundled (built-in) fonts, mapping the
     * case-insensitive font name to its case-sensitive classpath resource path.
     * <p>
     * The map is populated exactly once, on the first call to this method,
     * using the initialization-on-demand holder pattern. If several font files
     * share a name, the map holds the one discovered first (see the class Javadoc).
     *
     * @return unmodifiable case-insensitive map of built-in font names to resource paths
     */
    public static Map<String, String> getBuiltinFonts() {
        return FontHolder.INSTANCE;
    }

    /**
     * Resolves a built-in font name to its classpath resource path, preferring
     * a font file spelled exactly like {@code fontName}.
     *
     * @param fontName font name without extension
     * @return the resource path, or {@code null} if there is no such built-in font
     */
    static String resolveBuiltin(String fontName) {
        return resolve(fontName, FontHolder.EXACT, FontHolder.INSTANCE);
    }

    /**
     * Looks up {@code fontName} in {@code exact} first, then in {@code caseInsensitive}.
     *
     * @param fontName        font name without extension
     * @param exact           case-sensitive map of font names to resource paths
     * @param caseInsensitive case-insensitive map of font names to resource paths
     * @return the resource path, or {@code null} if neither map contains the name
     */
    static String resolve(String fontName, Map<String, String> exact, Map<String, String> caseInsensitive) {
        String resourcePath = exact.get(fontName);
        return resourcePath != null ? resourcePath : caseInsensitive.get(fontName);
    }

    /**
     * Loads and returns the {@link FigletFont} with the given name.
     * <p>
     * Built-in fonts are tried first, resolved as described in the class Javadoc;
     * externally registered fonts are consulted if no built-in font matches.
     * Parsed fonts are cached, so repeated calls for the same font do not
     * re-read or re-parse the underlying file.
     *
     * @param fontName font name, case-insensitive, without extension
     * @return the loaded font; never {@code null}
     * @throws FigletException      if no font with that name is found
     * @throws NullPointerException if {@code fontName} is {@code null}
     */
    public static FigletFont loadFont(String fontName) {
        requireNonNull(fontName, "fontName");
        String resourcePath = resolveBuiltin(fontName);
        if (resourcePath != null) {
            String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
            return FONT_CACHE.computeIfAbsent(resourcePath,
                p -> FigletFontLoader.loadBuiltin(FigletFontType.removeExtension(fileName), p));
        }
        String key = fontName.toLowerCase(Locale.ROOT);
        if (EXTERNAL_FONTS.containsKey(key)) {
            return FONT_CACHE.computeIfAbsent(EXTERNAL_PREFIX + key, k -> FigletFontLoader.loadFromFile(EXTERNAL_FONTS.get(key)));
        }
        throw new FigletException(String.format("No font named '%s' found. "
            + "Use the list-fonts goal to list all available fonts.", fontName));
    }

    /**
     * Registers an external {@code .flf} or {@code .tlf} font file.
     * Normalizes the name to lowercase.
     * <p>
     * Re-registering a name that was already loaded (and therefore cached)
     * invalidates that cache entry, so the next {@link #loadFont(String)} call
     * for this name re-parses the newly registered file instead of returning
     * the previously cached font. A concurrent {@link #loadFont(String)} still
     * parsing the previous file completes first, its result is then discarded.
     *
     * @param fontName logical font name without extension
     * @param path file-system path to the font file; must not be {@code null}
     * @throws NullPointerException if {@code fontName} or {@code path} is {@code null}
     */
    public static void registerExternal(String fontName, Path path) {
        requireNonNull(fontName, "fontName");
        requireNonNull(path, "path");
        LOGGER.log(Level.DEBUG, "Registering external font: {0} at {1}", fontName, path);
        String key = fontName.toLowerCase(Locale.ROOT);
        EXTERNAL_FONTS.put(key, path);
        FONT_CACHE.remove(EXTERNAL_PREFIX + key);
    }

    /**
     * Adds the font files directly in {@code path} to {@code fonts}, mapping each
     * font name to its resource path. Names already in {@code fonts} are kept,
     * and a {@code .tlf} font is added before a {@code .flf} font of the same name.
     *
     * @param path  the directory to scan; nothing is added if it does not exist
     * @param fonts the map to add to
     * @throws IOException if the directory cannot be read
     */
    static void scanPath(Path path, Map<String, String> fonts) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        List<String> fileNames;
        try (Stream<Path> walk = Files.walk(path, 1)) {
            fileNames = walk.filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(FigletFontType::isSupported)
                .sorted(Comparator.comparing(FigletFontRegistry::isFigfont).thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
        }
        for (String fileName : fileNames) {
            fonts.putIfAbsent(FigletFontType.removeExtension(fileName), FONTS_DIR + "/" + fileName);
        }
    }

    /**
     * Returns whether {@code fileName} is a FIGlet font, i.e. not a TOIlet font.
     *
     * @param fileName a font file name
     * @return {@code true} for a {@code .flf} file
     */
    private static boolean isFigfont(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(FigletFontType.FIGFONT.getExtension());
    }

    /**
     * Builds a case-insensitive map from {@code exact}, in which the first entry
     * of names that differ only in case wins.
     *
     * @param exact case-sensitive map of font names to resource paths, in discovery order
     * @return unmodifiable case-insensitive map
     */
    static NavigableMap<String, String> caseInsensitive(Map<String, String> exact) {
        NavigableMap<String, String> fonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        exact.forEach(fonts::putIfAbsent);
        return unmodifiableNavigableMap(fonts);
    }

    /**
     * Opens the {@link FileSystem} for {@code uri}, tracking whether it was
     * newly created or already existed.
     * <p>
     * The returned {@link OwnedFileSystem} only closes the underlying file
     * system on {@link OwnedFileSystem#close()} if it was created by this call;
     * a pre-existing file system (e.g. one already opened by the JVM's own
     * class-loading machinery, or concurrently by another thread) is left open
     * for its original owner. This avoids closing a file system that another
     * part of the JVM still depends on.
     *
     * @param uri the {@code jar:} URI identifying the file system
     * @return an {@link OwnedFileSystem} wrapping the resolved file system
     * @throws IOException if a new file system could not be created
     */
    static OwnedFileSystem openFileSystem(URI uri) throws IOException {
        try {
            return new OwnedFileSystem(FileSystems.getFileSystem(uri), false);
        } catch (FileSystemNotFoundException ex) {
            // no file system registered yet for this URI (the expected, common case) - create one;
            // any other exception (e.g. ProviderNotFoundException, SecurityException) is a real
            // error and must propagate instead of being misread as "not found yet"
            return new OwnedFileSystem(FileSystems.newFileSystem(uri, Map.of("create", "true")), true);
        }
    }

    /**
     * Wraps a {@link FileSystem} together with a flag indicating whether it was
     * opened (and is therefore owned and closeable) by the current caller, or
     * whether it pre-existed and must be left open for its original owner.
     */
    static final class OwnedFileSystem implements AutoCloseable {
        private final FileSystem fileSystem;
        private final boolean    owned;

        private OwnedFileSystem(FileSystem fileSystem, boolean owned) {
            this.fileSystem = fileSystem;
            this.owned = owned;
        }

        FileSystem get() {
            return fileSystem;
        }

        /** Returns {@code true} if this wrapper created the file system and therefore owns it. */
        boolean isOwned() {
            return owned;
        }

        /**
         * Closes the underlying file system only if it was opened by
         * {@link #openFileSystem(URI)} itself; a pre-existing file system is
         * left untouched so as not to break other users of it.
         */
        @Override
        public void close() throws IOException {
            if (owned) {
                fileSystem.close();
            }
        }
    }

    /**
     * Initialization-on-demand holder for the built-in font map.
     *
     * <p>
     * The JVM guarantees that {@code INSTANCE} is initialized exactly once,
     * at the time {@link FontHolder} is first accessed, without requiring explicit
     * synchronization.
     */
    static final class FontHolder {
        /** Built-in fonts by exact name, in discovery order. */
        static final Map<String, String> EXACT    = discoverBuiltinFonts();
        /** Built-in fonts by case-insensitive name; the font discovered first wins. */
        static final Map<String, String> INSTANCE = caseInsensitive(EXACT);

        private FontHolder() {
            throw new UnsupportedOperationException(
                String.format("Utility class %s cannot be instantiated", getClass().getSimpleName()));
        }

        /**
         * Scans the classpath for the {@code /fonts} directory and discovers all
         * available {@code .flf} and {@code .tlf} font files.
         *
         * @return unmodifiable map of font names to resource paths, in discovery order
         */
        private static Map<String, String> discoverBuiltinFonts() {
            Map<String, String> fonts = new LinkedHashMap<>();
            ClassLoader cl = FigletFontRegistry.class.getClassLoader();

            try {
                Enumeration<URL> resources = cl.getResources(FONTS_DIR);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    URI uri = url.toURI();

                    if ("jar".equals(uri.getScheme())) {
                        // handle packaged JAR file
                        try (OwnedFileSystem owned = openFileSystem(uri)) {
                            Path targetPath = owned.get().getPath("/" + FONTS_DIR);
                            scanPath(targetPath, fonts);
                        }
                    } else if ("file".equals(uri.getScheme())) {
                        // handle exploded directory (IDE / Build target)
                        Path targetPath = Paths.get(uri);
                        scanPath(targetPath, fonts);
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.ERROR, "Failed to discover built-in fonts", ex);
                throw new FigletException("Failed to discover built-in fonts", ex);
            }

            LOGGER.log(Level.DEBUG, "Discovered {0} built-in fonts", fonts.size());
            return unmodifiableMap(fonts);
        }

    }

}

