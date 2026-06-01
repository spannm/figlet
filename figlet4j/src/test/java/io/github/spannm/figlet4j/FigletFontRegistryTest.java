package io.github.spannm.figlet4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Unit tests for {@link FigletFontRegistry}.
 */
@SuppressWarnings({"checkstyle:MethodName", "PMD.LinguisticNaming"})
final class FigletFontRegistryTest {

    @Test
    void privateConstructor_shouldThrowUnsupportedOperationException()
            throws NoSuchMethodException {
        Constructor<?> constructor = FigletFontRegistry.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
            .isInstanceOf(InvocationTargetException.class)
            .cause()
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("Utility class %s cannot be instantiated", FigletFontRegistry.class.getSimpleName());
    }

    @Test
    void getBuiltinFonts_shouldReturnNonNullMap() {
        assertThat(FigletFontRegistry.getBuiltinFonts()).isNotNull();
    }

    @Test
    void getBuiltinFonts_shouldReturnUnmodifiableMap() {
        assertThatThrownBy(() -> FigletFontRegistry.getBuiltinFonts().put("x", "y"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getBuiltinFonts_calledTwice_shouldReturnSameInstance() {
        assertThat(FigletFontRegistry.getBuiltinFonts())
            .isSameAs(FigletFontRegistry.getBuiltinFonts());
    }

    @Test
    void getBuiltinFonts_whenFontsAreDiscovered_shouldContainStandardFonts() {
        // since fonts live in target/classes/fonts during IDE/Maven test execution,
        // we verify that the dynamic path scanning found the extracted test/standard fonts.
        Map<String, String> builtins = FigletFontRegistry.getBuiltinFonts();
        assertThat(builtins)
            .as("Built-in font registry should have dynamically discovered fonts")
            .isNotEmpty()
            .containsKey("standard");

        assertThat(builtins.get("standard"))
            .isEqualTo("fonts/standard.flf");
    }

    @Test
    void getBuiltinFonts_shouldBeCaseInsensitive() {
        Map<String, String> builtins = FigletFontRegistry.getBuiltinFonts();
        if (!builtins.isEmpty()) {
            String fontName = builtins.keySet().iterator().next();
            assertThat(builtins).containsKey(fontName.toUpperCase(Locale.ROOT));
            assertThat(builtins).containsKey(fontName.toLowerCase(Locale.ROOT));
        }
    }

    @Test
    void getBuiltinFonts_shouldBeSortedCaseInsensitively() {
        var fonts = new ArrayList<>(FigletFontRegistry.getBuiltinFonts().keySet());
        for (int i = 1; i < fonts.size(); i++) {
            assertThat(fonts.get(i - 1).compareToIgnoreCase(fonts.get(i)))
                .isLessThanOrEqualTo(0);
        }
    }

    @Test
    void listAllFonts_shouldContainAllBuiltinFonts() {
        assertThat(FigletFontRegistry.listAllFonts())
            .containsAll(FigletFontRegistry.getBuiltinFonts().keySet());
    }

    @Test
    void listAllFonts_shouldReturnUnmodifiableSet() {
        assertThatThrownBy(() -> FigletFontRegistry.listAllFonts().add("x"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void loadFont_nullName_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletFontRegistry.loadFont(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontName");
    }

    @Test
    void loadFont_unknownName_shouldThrowFigletException() {
        assertThatThrownBy(() -> FigletFontRegistry.loadFont("no-such-font-xyz"))
            .isInstanceOf(FigletException.class)
            .hasMessage("No font named 'no-such-font-xyz' found. Use the list-fonts goal to list all available fonts.");
    }

    @Test
    void loadFont_standardFont_shouldReturnValidFont() {
        FigletFont font = FigletFontRegistry.loadFont("standard");
        assertThat(font).isNotNull();
        assertThat(font.getName()).isEqualToIgnoringCase("standard");
        assertThat(font.getHeight()).isGreaterThan(0);
    }

    @Test
    void loadFont_allBuiltinFonts_shouldLoadWithoutException() {
        FigletFontRegistry.getBuiltinFonts().keySet().forEach(name ->
            assertThat(FigletFontRegistry.loadFont(name))
                .as("Font '%s' should load without exception", name)
                .isNotNull());
    }

    @Test
    void loadFont_calledTwice_shouldReturnCachedInstance() {
        FigletFont first  = FigletFontRegistry.loadFont("standard");
        FigletFont second = FigletFontRegistry.loadFont("STANDARD");

        assertThat(second)
            .as("repeated loadFont() calls for the same font must return the cached "
                + "instance instead of re-parsing the resource every time")
            .isSameAs(first);
    }

    @Test
    void registerExternal_nullName_shouldThrowNullPointerException(@TempDir Path tmp)
            throws IOException {
        Path fakeFlf = tmp.resolve("fake.flf");
        Files.writeString(fakeFlf, "");
        assertThatThrownBy(() -> FigletFontRegistry.registerExternal(null, fakeFlf))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("fontName");
    }

    @Test
    void registerExternal_nullPath_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> FigletFontRegistry.registerExternal("myfont", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("path");
    }

    @Test
    void registerExternal_validFont_shouldAppearInListAllFonts(@TempDir Path tmp)
            throws IOException {
        Path flfFile = tmp.resolve("mytest.flf");
        Files.writeString(flfFile, buildMinimalFlf(1));

        String uniqueName = "mytest-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, flfFile);

        assertThat(FigletFontRegistry.listAllFonts()).contains(uniqueName.toLowerCase(Locale.ROOT));
    }

    @Test
    void scanPath_withExplodedDirectory_shouldCorrectlyExtractFonts(@TempDir Path tmp)
            throws Exception {
        Path fontsDir = tmp.resolve("fonts");
        Files.createDirectories(fontsDir);
        Files.writeString(fontsDir.resolve("alpha.flf"), buildMinimalFlf(1));
        Files.writeString(fontsDir.resolve("beta.FLF"), buildMinimalFlf(1));
        Files.writeString(fontsDir.resolve("ignored.txt"), "not a font");

        Map<String, String> foundFonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        FigletFontRegistry.scanPath(fontsDir, foundFonts);

        assertThat(foundFonts)
            .hasSize(2)
            .containsEntry("alpha", "fonts/alpha.flf")
            .containsEntry("beta", "fonts/beta.FLF")
            .doesNotContainKey("ignored");
    }

    @Test
    void scanPath_withJarFileSystem_shouldCorrectlyExtractFonts(@TempDir Path tmp)
            throws Exception {
        Path jarFile = tmp.resolve("test-fonts.jar");

        Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        URI jarUri = URI.create("jar:" + jarFile.toUri());

        try (FileSystem zipfs = FileSystems.newFileSystem(jarUri, env)) {
            Path fontsInJar = zipfs.getPath("/fonts");
            Files.createDirectories(fontsInJar);
            Files.writeString(zipfs.getPath("/fonts/jarfont.flf"), buildMinimalFlf(1));
            Files.writeString(zipfs.getPath("/fonts/readme.md"), "info");
        }

        // verify reading from the packaged virtual file system context
        try (FileSystem zipfs = FileSystems.newFileSystem(jarUri, new HashMap<>())) {
            Map<String, String> foundFonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            FigletFontRegistry.scanPath(zipfs.getPath("/fonts"), foundFonts);

            assertThat(foundFonts).containsEntry("jarfont", "fonts/jarfont.flf");
        }
    }

    @Test
    void loadFont_registeredExternalFont_shouldLoadFromFile(@TempDir Path tmp) throws IOException {
        Path flfFile = tmp.resolve("extfont.flf");
        Files.writeString(flfFile, buildMinimalFlf(1));

        String uniqueName = "extfont-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, flfFile);

        FigletFont font = FigletFontRegistry.loadFont(uniqueName);

        assertThat(font).isNotNull();
        assertThat(font.getHeight()).isEqualTo(1);
    }

    @Test
    void loadFont_registeredExternalFont_shouldBeCaseInsensitive(@TempDir Path tmp) throws IOException {
        Path flfFile = tmp.resolve("extfont.flf");
        Files.writeString(flfFile, buildMinimalFlf(1));

        String uniqueName = "ExtFont-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, flfFile);

        assertThat(FigletFontRegistry.loadFont(uniqueName.toUpperCase(Locale.ROOT))).isNotNull();
        assertThat(FigletFontRegistry.loadFont(uniqueName.toLowerCase(Locale.ROOT))).isNotNull();
    }

    @Test
    void registerExternal_reRegisteredName_shouldInvalidateCachedFont(@TempDir Path tmp) throws IOException {
        Path firstFile = tmp.resolve("v1.flf");
        Files.writeString(firstFile, buildMinimalFlf(1));
        Path secondFile = tmp.resolve("v2.flf");
        Files.writeString(secondFile, buildMinimalFlf(2));

        String uniqueName = "reload-" + System.nanoTime();
        FigletFontRegistry.registerExternal(uniqueName, firstFile);
        assertThat(FigletFontRegistry.loadFont(uniqueName).getHeight()).isEqualTo(1);

        FigletFontRegistry.registerExternal(uniqueName, secondFile);

        assertThat(FigletFontRegistry.loadFont(uniqueName).getHeight())
            .as("re-registering a name must invalidate its cached font so the new file is loaded")
            .isEqualTo(2);
    }

    @Test
    void openFileSystem_newUri_shouldCreateAndOwnFileSystem(@TempDir Path tmp) throws IOException {
        Path jarFile = tmp.resolve("created.jar");
        URI jarUri = URI.create("jar:" + jarFile.toUri());

        FileSystem fs;
        try (FigletFontRegistry.OwnedFileSystem owned = FigletFontRegistry.openFileSystem(jarUri)) {
            fs = owned.get();
            assertThat(owned.isOwned()).isTrue();
            assertThat(fs.isOpen()).isTrue();
        }

        // newly created file systems are owned, so close() must actually close them
        assertThat(fs.isOpen()).isFalse();
        assertThat(Files.exists(jarFile)).isTrue();
    }

    @Test
    void openFileSystem_preExistingUri_shouldNotCloseForeignFileSystem(@TempDir Path tmp) throws IOException {
        Path jarFile = tmp.resolve("preexisting.jar");
        URI jarUri = URI.create("jar:" + jarFile.toUri());

        // simulate a file system already opened by someone else (e.g. class-loading machinery)
        try (FileSystem foreignFs = FileSystems.newFileSystem(jarUri, Map.of("create", "true"))) {
            try (FigletFontRegistry.OwnedFileSystem owned = FigletFontRegistry.openFileSystem(jarUri)) {
                assertThat(owned.isOwned())
                    .as("a file system resolved via getFileSystem() must not be considered owned")
                    .isFalse();
                assertThat(owned.get()).isSameAs(foreignFs);
            }

            // the pre-existing file system must still be open after our wrapper was closed
            assertThat(foreignFs.isOpen())
                .as("closing our OwnedFileSystem must not close a file system we did not create")
                .isTrue();
        }
    }

    @Test
    void scanPath_withNonExistentPath_shouldNotAddAnyFonts(@TempDir Path tmp) throws IOException {
        Path missing = tmp.resolve("does-not-exist");
        Map<String, String> fonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        FigletFontRegistry.scanPath(missing, fonts);

        assertThat(fonts).isEmpty();
    }

    @Test
    void scanPath_withEmptyDirectory_shouldReturnEmptyMap(@TempDir Path tmp) throws IOException {
        Path emptyDir = tmp.resolve("empty");
        Files.createDirectories(emptyDir);
        Map<String, String> fonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        FigletFontRegistry.scanPath(emptyDir, fonts);

        assertThat(fonts).isEmpty();
    }

    @Test
    void scanPath_withNestedSubdirectory_shouldNotDescendRecursively(@TempDir Path tmp) throws IOException {
        Path fontsDir = tmp.resolve("fonts");
        Path subDir = fontsDir.resolve("nested");
        Files.createDirectories(subDir);
        Files.writeString(fontsDir.resolve("top.flf"), buildMinimalFlf(1));
        Files.writeString(subDir.resolve("nested.flf"), buildMinimalFlf(1));

        Map<String, String> fonts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        FigletFontRegistry.scanPath(fontsDir, fonts);

        assertThat(fonts).containsOnlyKeys("top");
    }

    /**
     * Generates a minimal valid FLF file content string with the given height.
     * Produces the required 102 glyphs (ASCII 32–126 + 7 Deutsch) each consisting
     * of {@code height} rows of a single space terminated by {@code @} markers.
     *
     * @param height font height in rows (must be >= 1)
     * @return FLF file content as a string
     */
    private static String buildMinimalFlf(int height) {
        StringBuilder sb = new StringBuilder();
        // header: flf2a{hardblank} height baseline max_len old_layout comment_lines
        sb.append("flf2a$ ").append(height).append(" ").append(height)
          .append(" 2 0 0\n");
        // 95 ASCII (32-126) + 7 Deutsch = 102 required glyphs
        for (int i = 0; i < 102; i++) {
            sb.append(" @\n".repeat(Math.max(0, height - 1)))
              .append(" @@\n");
        }
        return sb.toString();
    }

}
