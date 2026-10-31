<div align="center">
  <a href="https://central.sonatype.com/artifact/io.github.spannm/figlet-maven-plugin"><img src="https://img.shields.io/maven-central/v/io.github.spannm/figlet-maven-plugin?label=Maven%20Central&style=flat-square" alt="Maven Central Version"></a>
  <img src="https://img.shields.io/maven-central/last-update/io.github.spannm/figlet-maven-plugin?label=Updated&style=flat-square&color=blue" alt="Maven Central Last Update">
  <a href="https://javadoc.io/doc/io.github.spannm/figlet4j"><img src="https://javadoc.io/badge2/io.github.spannm/figlet4j/javadoc.svg?style=flat-square" alt="Javadoc"></a>
  <img src="https://img.shields.io/badge/Java-11%2B-blue?style=flat-square" alt="Java 11+">
  <a href="https://github.com/spannm/figlet/blob/master/LICENSE.txt"><img src="https://img.shields.io/github/license/spannm/figlet?style=flat-square" alt="License"></a>
  <a href="https://github.com/spannm/figlet/stargazers"><img src="https://img.shields.io/github/stars/spannm/figlet?logo=github&label=&logoColor=white&labelColor=555555&color=007ec6&style=flat-square" alt="GitHub Stars"></a>
  <br>
  <a href="https://github.com/spannm/figlet/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/spannm/figlet/ci.yml?label=Build&style=flat-square" alt="GitHub Actions Build Status"></a>
  <a href="https://github.com/spannm/figlet/actions/workflows/codeql.yml"><img src="https://img.shields.io/github/actions/workflow/status/spannm/figlet/codeql.yml?label=CodeQL&style=flat-square" alt="CodeQL Workflow Status"></a>
  <a href="https://sonarcloud.io/summary/overall?id=io.github.spannm%3Afiglet"><img src="https://img.shields.io/sonar/quality_gate/io.github.spannm:figlet?server=https%3A%2F%2Fsonarcloud.io&style=flat-square" alt="SonarCloud Quality Gate"></a>
  <a href="https://sonarcloud.io/component_measures?id=io.github.spannm%3Afiglet&metric=coverage"><img src="https://img.shields.io/sonar/coverage/io.github.spannm:figlet?server=https%3A%2F%2Fsonarcloud.io&style=flat-square" alt="SonarCloud Coverage"></a>
</div>

<h1 align="center">figlet</h1>
<h3 align="center">ASCII art banners for your Maven build</h3>

**figlet** is a pure-Java library and Maven plugin for rendering text as ASCII art, powered by classic FIGfont (`.flf`) and TOIlet (`.tlf`) fonts. No native `figlet` binary, no external tools, it works anywhere a JVM runs.

```
  ░█▀▀░▀█▀░█▀▀░█░░░█▀▀░▀█▀
  ░█▀▀░░█░░█░█░█░░░█▀▀░░█░
  ░▀░░░▀▀▀░▀▀▀░▀▀▀░▀▀▀░░▀░
```

## Key Features

* **Pure Java, no third-party runtime dependencies** - `figlet4j` only pulls in its own bundled font resources; runs anywhere a JVM does.

* **A huge font collection, built in** - 146 FIGfonts bundled by default, plus 400 more via the optional `figlet-fonts-xero` module, about 250 of them not in the default set.

* **Maven-native** - `figlet-maven-plugin` prints a banner straight into your build log; it runs once per multi-module reactor, not once per module. It can also write the banner to a file, such as the `banner.txt` of a Spring Boot application.

* **Markup DSL** - mix several fonts and literal, unrendered text in a single banner, with Maven `${...}` property placeholder support.

* **Word-wrap, kerning & smushing** - automatic word-wrapping at a configurable width, with glyphs laid out as each font specifies: at full width, fitted together, or smushed like the original FIGlet does.

* **High test coverage** - JUnit 5 + AssertJ across the parser, renderer, font loader and every Mojo.

## Modules

| Module                   | Description                                                                                                                                      |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `figlet4j`               | Pure Java library for rendering text using FIGfonts (`.flf`) and TOIlet fonts (`.tlf`)                                                           |
| `figlet-fonts-figletorg` | 146 standard FIGfont resources collected from [figlet.org](https://www.figlet.org)                                                               |
| `figlet-fonts-xero`      | 400 FIGfonts and TOIlet fonts from the [xero/figlet-fonts](https://github.com/xero/figlet-fonts) repository, a superset of the figlet.org fonts |
| `figlet-maven-plugin`    | Maven plugin - print ASCII art banners during your build                                                                                         |

## Tech Stack & Requirements

* **To use `figlet-maven-plugin`**: Maven 3.6.0+ running on Java 11 or higher.
* **To use `figlet4j`** as a standalone library: Java 11 or higher.
* **To build this project from source**: JDK 21+ and Maven 3.9.0+ (enforced by the build itself; the *compiled* artifacts still target Java 11).

## Installation

### As a Maven plugin

Add to your `pom.xml`:

```xml
<plugin>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet-maven-plugin</artifactId>
  <version>1.2.0</version>
  <executions>
    <execution>
      <goals>
        <goal>render</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The `<execution>` is required, otherwise the goal is never invoked. It binds to the `validate` phase by default, so no `<phase>` is needed. In a multi-module build, declare it once in the parent POM.

Without further configuration, the project name is rendered in the `standard` font, followed by the version:

```
[INFO] --- figlet:1.2.0:render (default) @ my-app ---
[INFO]
[INFO]
[INFO]  _ __ ___  _   _        __ _ _ __  _ __
[INFO] | '_ ` _ \| | | |_____ / _` | '_ \| '_ \
[INFO] | | | | | | |_| |_____| (_| | |_) | |_) |
[INFO] |_| |_| |_|\__, |      \__,_| .__/| .__/
[INFO]            |___/            |_|   |_|
[INFO]
[INFO]     v1.0.0
[INFO]
```

Use `<content>` and `<font>` in a `<configuration>` block to change what is rendered and how, see [`render` configuration](#render-configuration). All fonts of both font modules are available to the plugin.

### As a Java library (`figlet4j`)

**Maven (`pom.xml`)**

```xml
<dependency>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet4j</artifactId>
  <version>1.2.0</version>
</dependency>
```

**Gradle (Groovy / `build.gradle`)**

```groovy
implementation 'io.github.spannm:figlet4j:1.2.0'
```

**Gradle (Kotlin DSL / `build.gradle.kts`)**

```kotlin
implementation("io.github.spannm:figlet4j:1.2.0")
```

`figlet4j` comes with the 146 figlet.org fonts. For the extended collection, add `figlet-fonts-xero` as well, its fonts are picked up from the classpath automatically:

```xml
<dependency>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet-fonts-xero</artifactId>
  <version>1.2.0</version>
</dependency>
```

## Quick Start

```java
FigletFont font = FigletFontRegistry.loadFont("standard");
FigletRenderer renderer = new FigletRenderer(font).withWidth(100);
System.out.println(renderer.render("Hello World"));
```

```
 _   _      _ _        __        __         _     _
| | | | ___| | | ___   \ \      / /__  _ __| | __| |
| |_| |/ _ \ | |/ _ \   \ \ /\ / / _ \| '__| |/ _` |
|  _  |  __/ | | (_) |   \ V  V / (_) | |  | | (_| |
|_| |_|\___|_|_|\___/     \_/\_/ \___/|_|  |_|\__,_|
```

`FigletFontRegistry` finds fonts by name, case-insensitively, and caches them once parsed. A few more things the library can do:

```java
// lenient mode prints '?' for characters the font lacks instead of failing,
// and a handler can report them
String banner = new FigletRenderer(FigletFontRegistry.loadFont("slant"))
    .setStrict(false)
    .withUnsupportedHandler((font, codepoints) -> log.warn("Incomplete banner: {}", codepoints))
    .render("Hello, World!");

// which characters a font can render, and which of a text it cannot
FigletFont font = FigletFontRegistry.loadFont("slant");
SortedSet<Integer> supported = font.getSupportedCodepoints();
Set<Integer> missing = font.findUnsupportedCodepoints("Grüße");

// fonts outside the classpath, loaded directly or registered under a name
FigletFont custom = FigletFontLoader.loadFromFile(Path.of("fonts/myfont.flf"));
FigletFontRegistry.registerExternal("myfont", Path.of("fonts/myfont.flf"));

// several fonts and plain text in one banner, using the markup DSL
String mixed = new FigletMarkupRenderer(FigletFontRegistry::loadFont)
    .render("<figletFont name=\"small\">v1.2.0</figletFont><lineBreak/>built today");
```

The [markup DSL](#markup-dsl) is described below.

## Goals

| Goal                | Default phase | Description                                  |
|---------------------|---------------|----------------------------------------------|
| `figlet:render`     | `validate`    | Renders ASCII art to the build log or a file |
| `figlet:list-fonts` | _(none)_      | Lists all available fonts with metadata      |
| `figlet:help`       | _(none)_      | Shows usage and credits                      |

> **Note:** In a multi-module reactor build, `figlet:render` by default only executes
> for the execution-root project (the module Maven was invoked on) and does nothing
> for every other module, so the same banner is not printed once per module. Set
> `executionRootOnly` to `false` in a module that should render its own banner.

### `render` configuration

| Parameter           | Property                   | Default                                   | Description                                                                                                                         |
|---------------------|----------------------------|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `content`           | `figlet.content`           | _(project name as banner, version below)_ | Content to render; supports `${...}` property placeholders and the [markup DSL](#markup-dsl) to mix multiple fonts with plain text |
| `font`              | `figlet.font`              | `standard`                                | Name of a built-in font (see `figlet:list-fonts`)                                                                                   |
| `fontFile`          | `figlet.fontFile`          | _(none)_                                  | Path to an external `.flf` or `.tlf` file; takes precedence over `font` when both are set                                          |
| `width`             | `figlet.width`             | `72`                                      | Max output width; long lines wrap at word boundaries                                                                                |
| `strict`            | `figlet.strict`            | `true`                                    | Fail if the content contains characters not supported by the font, or a `${...}` placeholder cannot be resolved. If `false`, both are logged as warnings |
| `target`            | `figlet.target`            | `info`                                    | Where to write the banner: `info`, `debug`, `stdout`, or `stderr`                                                                  |
| `outputFile`        | `figlet.outputFile`        | _(none)_                                  | Write the banner to this file (UTF-8) instead of the build log, e.g. a Spring Boot `banner.txt`; ignores `executionRootOnly`       |
| `executionRootOnly` | `figlet.executionRootOnly` | `true`                                    | Only render for the execution-root project of a multi-module build                                                                  |
| `skip`              | `figlet.skip`              | `false`                                   | Skip this goal entirely                                                                                                             |

### `list-fonts` configuration

| Parameter     | Property            | Default    | Description                                                                                 |
|---------------|---------------------|------------|---------------------------------------------------------------------------------------------|
| `metadata`    | `figlet.metadata`   | `false`    | Load every font and print its metadata comment block (author, date, ...) and its characters |
| `printSample` | `figlet.sample`     | `false`    | Render a short ASCII-art sample for every listed font                                       |
| `sampleText`  | `figlet.sampleText` | _(random)_ | Custom sample text; defaults to a weekday, month, year or the like                          |
| `strict`      | `figlet.strict`     | `false`    | List only the fonts that can render `sampleText` completely                                 |
| `width`       | `figlet.width`      | `72`       | Max width of the samples; long lines wrap at word boundaries                                |
| `skip`        | `figlet.skip`       | `false`    | Skip this goal entirely                                                                     |

## Markup DSL

`content` may mix ASCII-art banners in different fonts with literal, unrendered text:

```xml
<content><![CDATA[
  <figletFont name="standard">${project.name}</figletFont><lineBreak/>
  <figletFont name="small">${project.version}</figletFont><lineBreak/>
  built by ${user.name}
]]></content>
```

- `<figletFont name="...">...</figletFont>` - enclosed text is rendered as ASCII art using the named built-in font. Without the `name` attribute, `<figletFont>...</figletFont>` uses the font configured via `font`/`fontFile`.
- `<lineBreak/>` - an explicit line break, independent of font or automatic word-wrapping.
- `<preserveWhitespace>...</preserveWhitespace>` - enclosed text is emitted verbatim (leading/trailing whitespace and blank lines kept), unlike plain text which is trimmed.
- Everything else is literal text, printed as-is.
- If `content` contains no `<figletFont>` tag at all, the whole (resolved) content is rendered as a single banner using `font`/`fontFile`, exactly as if it had been wrapped in one `<figletFont>` tag referencing that font.

## Spring Boot banner

Spring Boot prints the content of a `banner.txt` on the classpath at startup. Since version 1.2.0 the plugin can generate that file at build time, so the application needs no runtime dependency on figlet:

```xml
<plugin>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet-maven-plugin</artifactId>
  <version>1.2.0</version>
  <executions>
    <execution>
      <goals>
        <goal>render</goal>
      </goals>
      <configuration>
        <font>slant</font>
        <content><![CDATA[
          <figletFont>${project.name}</figletFont><lineBreak/>
          <preserveWhitespace>  v${project.version}</preserveWhitespace>
        ]]></content>
        <outputFile>${project.build.outputDirectory}/banner.txt</outputFile>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The goal runs in the `validate` phase, before the resources are copied, so do not keep a `banner.txt` of your own in `src/main/resources`, it would overwrite the generated one. Placeholders are resolved by Maven at build time. A Spring placeholder such as `${application.version}` in plain text is left for Spring only with `strict` set to `false`, otherwise it fails the build as unresolved. In a multi-module build, configure the plugin in the POM of the Spring Boot module itself rather than in a shared parent, where every module would get a `banner.txt`. `executionRootOnly` does not apply with `outputFile`, so the file is written although that module is usually not the execution root.

To render the banner at runtime instead, use figlet4j directly:

```java
SpringApplication app = new SpringApplication(MyApplication.class);
app.setBanner((environment, sourceClass, out) ->
    out.println(new FigletRenderer(FigletFontRegistry.loadFont("slant")).render("My App")));
app.run(args);
```

## Fonts

All fonts can be browsed online in the [font gallery](https://spannm.github.io/figlet/), with a sample, the character set and usage snippets for each font.

Locally, the `list-fonts` goal lists every available font, and `-Dfiglet.sample=true` renders a sample of each one:

```bash
mvn io.github.spannm:figlet-maven-plugin:list-fonts -Dfiglet.sample=true
```

A sample that misses characters is followed by a note naming them. With `-Dfiglet.strict=true -Dfiglet.sampleText=...`, only the fonts that can render the text completely are listed.

In a project that declares the plugin in its POM, the short form `mvn figlet:list-fonts` works as well. Elsewhere Maven cannot resolve the `figlet` prefix, unless `io.github.spannm` is added to the `<pluginGroups>` in your `settings.xml`.

> Font files retain their original licenses, see the `LICENSE.txt` in each `figlet-fonts-*` module for details.

## Building from source

```bash
git clone --recurse-submodules https://github.com/spannm/figlet.git
cd figlet
mvn
```

> **Note:** The project uses a Git submodule (`figlet-fonts-xero`) for its extended font collection. Make sure to clone with `--recurse-submodules`, or run `git submodule update --init --recursive` after cloning.

## Contributions welcome!

Got a bug to fix or a feature to add?

1. Check out the [Issues](https://github.com/spannm/figlet/issues)
2. [Fork](https://github.com/spannm/figlet/fork) the repo
3. Submit a [Pull Request](https://github.com/spannm/figlet/pulls)

*Note: Please ensure your code follows the project's quality standards (Checkstyle and Error Prone are enforced during the build).*

<div align="center"> ─────────────── </div>

### License

figlet is licensed under the **Apache License, Version 2.0**.

Font files retain their original licenses. See the respective `LICENSE.txt` or notice files within the font resource modules for detailed third-party licensing information.

## Credits

* **FIGlet** - Glenn Chappell, Ian Chai, Frank Sheeran (1991) - https://www.figlet.org
* **FIGfont spec** - John Cowan, Paul Burton (1996/1997) - https://www.jave.de/figlet/figfont.html
* **TOIlet** - Sam Hocevar (2006) - http://caca.zoy.org/wiki/toilet
* **jfiglet** - Lajos Puskas, inspired by Benoît Rigaut's CERN implementation - https://github.com/lalyos/jfiglet
* **xero/figlet-fonts** - the font collection behind `figlet-fonts-xero` - https://github.com/xero/figlet-fonts

<p style="height: 40px;">&nbsp;</p>

<div align="center">
<table style="border-collapse: collapse;">
  <tr>
    <td style="padding: 40px; border: 2px solid #3a82c2;">
      <strong>Enjoying figlet? Please leave a 🌟 to support the project!</strong><br>
      <small>Your stars help others discover the project and keep it maintained.</small>
    </td>
  </tr>
</table>
</div>
