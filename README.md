# figlet

ASCII art banners for your Maven build - powered by FIGfonts and TOIlet fonts.

```
  ___  __         __         __    _____    __
.'  _||__|.-----.|  |.-----.|  |_ |  |  |  |__|
|   _||  ||  _  ||  ||  -__||   _||__    | |  |
|__|  |__||___  ||__||_____||____|   |__|  |  |
          |_____|                         |___|
```

## Modules

| Module                   | Description                                                                                                                    |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `figlet4j`               | Pure Java library for rendering text using FIGfonts (`.flf`) and TOIlet fonts (`.tlf`)                                         |
| `figlet-fonts-figletorg` | Standard FIGfont resources collected from [figlet.org](https://www.figlet.org)                                                 |
| `figlet-fonts-xero`      | Extended collection of FIGfonts and TOIlet fonts from the [xero/figlet-fonts](https://github.com/xero/figlet-fonts) repository |
| `figlet-maven-plugin`    | Maven plugin — print ASCII art banners during your build                                                                       |

## Quick start

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

## Goals

| Goal                | Default phase | Description                             |
|---------------------|---------------|-----------------------------------------|
| `figlet:render`     | `validate`    | Renders ASCII art to the build log      |
| `figlet:list-fonts` | _(none)_      | Lists all available fonts with metadata |
| `figlet:help`       | _(none)_      | Shows usage and credits                 |

> **Note:** In a multi-module reactor build, `figlet:render` only executes for the
> execution-root project (the module Maven was invoked on) and silently does
> nothing for every other module — even if the plugin is bound in a child
> module's own `pom.xml`. This avoids printing the same banner once per module.

## Configuration reference (`render`)

| Parameter  | Property          | Default           | Description                                                      |
|------------|-------------------|-------------------|------------------------------------------------------------------|
| `content`  | `figlet.content`  | `${project.name}` | Content to render; supports `${...}` property placeholders and the [markup DSL](#markup-dsl) to mix multiple fonts with plain text |
| `font`     | `figlet.font`     | `standard`        | Name of a built-in font (see `figlet:list-fonts`)                |
| `fontFile` | `figlet.fontFile` | _(none)_          | Path to an external `.flf` or `.tlf` file                        |
| `width`    | `figlet.width`    | `72`              | Max output width; long lines wrap at word boundaries             |
| `strict`   | `figlet.strict`   | `true`            | Fail if the content contains characters not supported by the font, or a `${...}` placeholder cannot be resolved |
| `target`   | `figlet.target`   | `info`            | Where to write the banner: `info`, `debug`, `stdout`, or `stderr` |
| `skip`     | `figlet.skip`     | `false`           | Skip this goal entirely                                          |

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
- If `content` contains no `<figletFont>` tag at all, the whole (resolved) content is rendered as a single banner using `font`/`fontFile` — exactly like in previous versions.

## Using figlet4j as a standalone library

```xml
<dependency>
  <groupId>io.github.spannm</groupId>
  <artifactId>figlet4j</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
FigletFont font = FigletFontLoader.loadBuiltin("standard");
FigletRenderer r = new FigletRenderer(font).withWidth(100);
System.out.println(r.render("Hello World"));
```

## Building from source

```bash
git clone --recurse-submodules https://github.com/spannm/figlet.git
cd figlet
mvn
```

> **Note:** The project uses Git submodules for extended font resources. Make sure to clone with `--recurse-submodules` or run `git submodule update --init --recursive` after cloning.

Requires Java 11+ and Maven 3.6.3+.

## Credits

* **FIGlet** — Glenn Chappell, Ian Chai, Frank Sheeran (1991) — https://www.figlet.org
* **TOIlet** — Sam Hocevar (2006) — localhost/libcaca TOIlet tools (Sub-set specification support)
* **FIGfont spec** — John Cowan, Paul Burton (1996/1997)
* **jfiglet** — Lajos Puskas, inspired by Benoît Rigaut's CERN implementation

## License

Apache License, Version 2.0.

Font files retain their original licenses. See the respective `LICENSE.txt` or notice files within the font resource modules for detailed third-party licensing information.

<p style="height: 40px;">&nbsp;</p>

<div align="center">
<table style="border-collapse: collapse;">
  <tr>
    <td style="padding: 40px; border: 2px solid #3a82c2;">
      <strong>Enjoying figlet? Please leave a 🌟 to support the project!</strong>
    </td>
  </tr>
</table>
</div>
