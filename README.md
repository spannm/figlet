<div align="center">
  <a href="https://central.sonatype.com/artifact/io.github.spannm/figlet-maven-plugin"><img src="https://img.shields.io/maven-central/v/io.github.spannm/figlet-maven-plugin?label=Maven%20Central&style=flat-square" alt="Maven Central Version"></a>
  <img src="https://img.shields.io/maven-central/last-update/io.github.spannm/figlet-maven-plugin?label=Updated&style=flat-square&color=blue" alt="Maven Central Last Update">
  <a href="https://github.com/spannm/figlet/stargazers"><img src="https://img.shields.io/github/stars/spannm/figlet?logo=github&label=&logoColor=white&labelColor=555555&color=007ec6&style=flat-square" alt="GitHub Stars"></a>
  <br>
  <a href="https://github.com/spannm/figlet/actions/workflows/ci.yml"><img src="https://img.shields.io/github/actions/workflow/status/spannm/figlet/ci.yml?label=Build&style=flat-square" alt="GitHub Actions Build Status"></a>
  <a href="https://github.com/spannm/figlet/actions/workflows/codeql.yml"><img src="https://img.shields.io/github/actions/workflow/status/spannm/figlet/codeql.yml?label=CodeQL&style=flat-square" alt="CodeQL Workflow Status"></a>
  <a href="https://javadoc.io/doc/io.github.spannm/figlet4j"><img src="https://javadoc.io/badge2/io.github.spannm/figlet4j/javadoc.svg?style=flat-square" alt="Javadoc"></a>
</div>

<h1 align="center">figlet</h1>
<h3 align="center">ASCII art banners for your Maven build</h3>

**figlet** is a pure-Java library and Maven plugin for rendering text as ASCII art, powered by classic FIGfont (`.flf`) and TOIlet (`.tlf`) fonts — no native `figlet` binary, no external tools, works anywhere a JVM runs.

```
  ░█▀▀░▀█▀░█▀▀░█░░░█▀▀░▀█▀
  ░█▀▀░░█░░█░█░█░░░█▀▀░░█░
  ░▀░░░▀▀▀░▀▀▀░▀▀▀░▀▀▀░░▀░
```

## Key Features

* **Pure Java, no third-party runtime dependencies** — `figlet4j` only pulls in its own bundled font resources; runs anywhere a JVM does.

* **A huge font collection, built in** — 146 FIGfonts bundled by default, plus 400+ more via the optional `figlet-fonts-xero` module.

* **Maven-native** — `figlet-maven-plugin` prints a banner straight into your build log; it runs once per multi-module reactor, not once per module.

* **Markup DSL** — mix several fonts and literal, unrendered text in a single banner, with Maven `${...}` property placeholder support.

* **Word-wrap & kerning** — automatic word-wrapping at a configurable width, with glyphs fitted together instead of separated by ragged whitespace.

* **High test coverage** — JUnit 5 + AssertJ across the parser, renderer, font loader and every Mojo.

## Modules

| Module                   | Description                                                                                                              |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `figlet4j`               | Pure Java library for rendering text using FIGfonts (`.flf`) and TOIlet fonts (`.tlf`)                                   |
| `figlet-fonts-figletorg` | 146 standard FIGfont resources collected from [figlet.org](https://www.figlet.org)                                      |
| `figlet-fonts-xero`      | 400+ additional FIGfonts and TOIlet fonts from the [xero/figlet-fonts](https://github.com/xero/figlet-fonts) repository |
| `figlet-maven-plugin`    | Maven plugin — print ASCII art banners during your build                                                                 |

## Tech Stack & Requirements

* **To use `figlet-maven-plugin`**: Maven 3.6.0+ and any JVM — the published artifacts target Java 11 bytecode.
* **To use `figlet4j`** as a standalone library: Java 11 or higher.
* **To build this project from source**: JDK 17+ and Maven 3.9.0+ (enforced by the build itself; the *compiled* artifacts still target Java 11).

## Installation

### As a Maven plugin

Add to your `pom.xml`:

```xml
<plugin>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <content>${project.name} ${project.version}</content>
    <font>chunky</font>
  </configuration>
</plugin>
```

### As a Java library (`figlet4j`)

**Maven (`pom.xml`)**

```xml
<dependency>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet4j</artifactId>
  <version>1.0.0</version>
</dependency>
```

**Gradle (Groovy / `build.gradle`)**

```groovy
implementation 'io.github.spannm:figlet4j:1.0.0'
```

**Gradle (Kotlin DSL / `build.gradle.kts`)**

```kotlin
implementation("io.github.spannm:figlet4j:1.0.0")
```

## Quick Start

```java
FigletFont font = FigletFontLoader.loadBuiltin("standard");
FigletRenderer renderer = new FigletRenderer(font).withWidth(100);
System.out.println(renderer.render("Hello World"));
```

Or mix multiple fonts and plain text during a Maven build with the [markup DSL](#markup-dsl) below.

## Goals

| Goal                | Default phase | Description                             |
|----------------------|---------------|-----------------------------------------|
| `figlet:render`     | `validate`    | Renders ASCII art to the build log      |
| `figlet:list-fonts` | _(none)_      | Lists all available fonts with metadata |
| `figlet:help`       | _(none)_      | Shows usage and credits                 |

> **Note:** In a multi-module reactor build, `figlet:render` only executes for the
> execution-root project (the module Maven was invoked on) and silently does
> nothing for every other module — even if the plugin is bound in a child
> module's own `pom.xml`. This avoids printing the same banner once per module.

### `render` configuration

| Parameter  | Property          | Default           | Description                                                      |
|------------|-------------------|-------------------|--------------------------------------------------------------------|
| `content`  | `figlet.content`  | `${project.name}` | Content to render; supports `${...}` property placeholders and the [markup DSL](#markup-dsl) to mix multiple fonts with plain text |
| `font`     | `figlet.font`     | `standard`        | Name of a built-in font (see `figlet:list-fonts`)                |
| `fontFile` | `figlet.fontFile` | _(none)_          | Path to an external `.flf` or `.tlf` file; takes precedence over `font` when both are set |
| `width`    | `figlet.width`    | `72`              | Max output width; long lines wrap at word boundaries             |
| `strict`   | `figlet.strict`   | `true`            | Fail if the content contains characters not supported by the font, or a `${...}` placeholder cannot be resolved |
| `target`   | `figlet.target`   | `info`            | Where to write the banner: `info`, `debug`, `stdout`, or `stderr` |
| `skip`     | `figlet.skip`     | `false`           | Skip this goal entirely                                          |

### `list-fonts` configuration

| Parameter    | Property             | Default    | Description                                                             |
|--------------|-----------------------|------------|---------------------------------------------------------------------------|
| `metadata`   | `figlet.metadata`    | `false`    | Load every font and print its metadata comment block (author, date, …) |
| `sample`     | `figlet.sample`      | `false`    | Render a short ASCII-art sample for every listed font                  |
| `sampleText` | `figlet.sampleText`  | _(random)_ | Custom sample text; defaults to a varied, always-renderable fallback   |
| `skip`       | `figlet.skip`        | `false`    | Skip this goal entirely                                                |

## Markup DSL

`content` may mix ASCII-art banners in different fonts with literal, unrendered text:

```xml
<content><![CDATA[
  <figletFont name="standard">${project.name}</figletFont><lineBreak/>
  <figletFont name="small">${project.version}</figletFont><lineBreak/>
  built by ${user.name}
]]></content>
```

- `<figletFont name="...">...</figletFont>` — enclosed text is rendered as ASCII art using the named built-in font.
- `<lineBreak/>` — an explicit line break, independent of font or automatic word-wrapping.
- `<preserveWhitespace>...</preserveWhitespace>` — enclosed text is emitted verbatim (leading/trailing whitespace and blank lines kept), unlike plain text which is trimmed.
- Everything else is literal text, printed as-is.
- If `content` contains no `<figletFont>` tag at all, the whole (resolved) content is rendered as a single banner using `font`/`fontFile` — exactly as if it had been wrapped in one `<figletFont>` tag referencing that font.

## Fonts

Run `mvn figlet:list-fonts` to browse every font on your classpath — add `-Dfiglet.sample=true` to render a live sample of each one.

> Font files retain their original licenses — see the `LICENSE.txt` in each `figlet-fonts-*` module for details.

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

* **FIGlet** — Glenn Chappell, Ian Chai, Frank Sheeran (1991) — https://www.figlet.org
* **TOIlet** — Sam Hocevar (2006) — localhost/libcaca TOIlet tools (Sub-set specification support)
* **FIGfont spec** — John Cowan, Paul Burton (1996/1997)
* **jfiglet** — Lajos Puskas, inspired by Benoît Rigaut's CERN implementation

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
