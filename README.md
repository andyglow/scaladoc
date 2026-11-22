# Scaladoc

[![CI](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/andyglow/scaladoc/branch/master/graph/badge.svg)](https://codecov.io/gh/andyglow/scaladoc)
[![scala 2.12](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_2.12?label=scala%202.12)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_2.12)
[![scala 2.13](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_2.13?label=scala%202.13)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_2.13)
[![scala 3](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_3?label=scala%203)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_3)

A toolset for working with scaladoc comments from Scala code:
- **AST**: a model of scaladoc tags and their markdown markup
- **Parser**: turns comment text into that model
- **Extractor**: `Scaladoc.of[T]`, which gets the scaladoc of a type at compile time
- **Compiler plugin** (Scala 2): embeds the scaladoc of classes into their bytecode, so tools can read it later,
  even from jars

> Original `Scaladoc` [documentation](https://docs.scala-lang.org/overviews/scaladoc/for-library-authors.html)

## Supported versions

| Module                     | Scala 2.12 | Scala 2.13 | Scala 3 (3.3+) |
|----------------------------|:----------:|:----------:|:--------------:|
| `scaladoc-ast`             |     ✓      |     ✓      |       ✓        |
| `scaladoc-parser`          |     ✓      |     ✓      |       ✓        |
| `scaladoc-compiler-plugin` |     ✓      |     ✓      |       –        |

`scaladoc-ast` and `scaladoc-parser` are also built for Scala.js 1.x (`_sjs1`, from 0.3.0), on all three Scala
versions. The compiler plugin runs inside scalac, so its JVM artifact works for Scala.js projects as well.

`sbt-scaladoc` (from 0.2.0) works with sbt 1.5.8+ and sbt 2.x.

All artifacts target Java 8 and work on any later JDK. Scala 2.11 is supported up to version 0.0.14.

> **The groupId changed in 0.1.0** from `com.github.andyglow` to `io.github.andyglow`. Versions up to 0.0.14 stay
> available under `com.github.andyglow`. To upgrade, change the groupId along with the version.

## Usage

### With the sbt plugin
`sbt-scaladoc` (from 0.2.0) sets a module up for you. Add it to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "0.3.0")
```

and enable it on the modules that need it:

```sbt
lazy val model = project
  .enablePlugins(ScaladocPlugin)
  .settings(
    scaladocExcludeClasses := Seq("com.acme.**.internal.*") // optional, see "Choosing what to embed"
  )
```

- **Scala 2:** it adds the compiler plugin, which embeds the scaladoc of the module's classes into their bytecode, plus
  `scaladoc-ast` and `scaladoc-parser`. Set `scaladocEmbed := false` on modules that only read scaladoc.
- **Scala 3:** the compiler plugin doesn't exist there, so it adds `scaladoc-ast`, `scaladoc-parser` and `-Yread-docs`.
  That lets `Scaladoc.of` read doc comments from other modules and jars.

The libraries get the plugin's own version; override it with `scaladocVersion` if needed. On Scala.js modules (from
0.3.0) it adds the `_sjs1` libraries and the JVM compiler plugin.

### By hand

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "0.3.0"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "0.3.0"
```

To embed scaladocs into bytecode (Scala 2 only), also add the compiler plugin:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "0.3.0")

libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "0.3.0"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "0.3.0" % Provided
```

On Scala.js, use `%%%` for `scaladoc-ast` and `scaladoc-parser`. The compiler plugin stays `%%`.

## Scaladoc AST
The model gives a detailed view of a description. It covers the scaladoc tags and the wiki markup of scaladoc
(`''italic''`, `[[links]]`, `{{{code}}}`, ...) in the tags that allow it. Inline markup nests, as in `'''bold ''italic'''''`; the text of a `Monospace` is taken as
is.

```
Span     = PlainText (String)
         | Monospace (String)
         | Italic (List Span)
         | Bold (List Span)
         | Underline (List Span)
         | Superscript (List Span)
         | Subscript (List Span)
         | Link (String (List Span))      -- target, title
         | HtmlTag (String)               -- an HTML tag the model has no equivalent of

Markup   = Span
         | Heading (Level (List Span))
         | Paragraph (List Span)
         | CodeBlock (String)
         | UnorderedList (List Document)          -- an item: its text and nested lists
         | OrderedList (List Document Style)      -- Style: 1. i. I. a. A.
         | Table (Row (List Alignment) (List Row))  -- a Row is a List Paragraph, one a cell
         | HorizontalRule
         | Document (List Markup)

Tag      = Description       (Markup)
         | Constructor       (Markup)
         | Param             (String Markup)
         | TypeParam         (String Markup)
         | Returns           (Markup)
         | Throws            (String Markup)
         | See               (Markup)
         | Note              (Markup)
         | Example           (Markup)
         | UseCase           (Markup)
         | Author            (String)
         | Version           (String)
         | Since             (String)
         | Todo              (Markup)
         | Deprecated        (Markup)
         | Migration         (Markup)
         | Group             (String)
         | GroupName         (String String)
         | GroupDescription  (String Markup)
         | GroupPriority     (String Int)
         | Documentable
         | InheritDoc
         | OtherTag          (String Markup)

Scaladoc = List Tag
```

## Parser
The parser takes comment text and returns either the parsed model or an error. It is plain Scala over `String`;
on Scala 3 its only dependency is `scaladoc-ast`.

```scala
import scaladoc._

val text = """/** = Title =
             |  *
             |  * Description
             |  *
             |  * @param a Param A
             |  * @param b Param B
             |  */
             |""".stripMargin

val scaladoc: Either[ScaladocException, Scaladoc] = Scaladoc.fromString(text)
```

Scala 3's `Symbol.docstring` returns the comment as written, except that the indentation of its first line is
gone. `Scaladoc.fromDocstring` parses that form. With `strict = true` it also checks that the `*` border is
aligned, the same way the Scala 2 extractor does.

```scala
import scaladoc._

val scaladoc = Scaladoc.fromDocstring("/** Description\n    *\n    * @param a Param A\n    */", strict = true)
```

## Extractor
`Scaladoc.of[T]` gets the scaladoc of `T` at compile time, which is useful in macros and in derivation.

```scala
import scaladoc._

val scaladoc: Option[Scaladoc] = Scaladoc.of[Foo]
```

**Scala 2** checks these places in order:
1. macro attachments
2. the `@ScaladocCarrier` annotation that the compiler plugin adds to the bytecode
3. the source code, if it is available

**Scala 3** reads `Symbol.docstring`. By default the compiler only keeps the doc comments of the sources it compiles
in the current run. To read them from TASTy too (other modules, jars and files skipped by an incremental compile),
compile the calling module with `-Yread-docs`:

```sbt
scalacOptions += "-Yread-docs"
```

Source-based extraction on Scala 2 has the same limitation with incremental compiles. The compiler plugin avoids it
by storing the scaladoc in the bytecode.

## Compiler plugin
Scala lets us derive a lot from what we already have: case classes (products), sealed traits (sums) and so on.
Sometimes you also want your models documented, and that documentation shipped with the bytecode so other tools can
use it. For example, tools that derive JSON Schema, Avro schemas, Swagger or OpenAPI specs can add titles and
descriptions taken from the scaladoc.

The plugin (Scala 2.12 and 2.13):
- validates the scaladoc
- packs it into a Java annotation, so that it is carried over in the bytecode

### Choosing what to embed
By default the plugin embeds the scaladoc of every documented class. Include and exclude patterns narrow that down.
With `sbt-scaladoc`, set them as settings:

```sbt
scaladocIncludePaths   := Seq("**/model/**")
scaladocIncludeClasses := Seq("com.acme.api.*")
scaladocExcludePaths   := Seq("**/generated/**")
scaladocExcludeClasses := Seq("com.acme.**.internal.*")
```

Without it, pass them to the compiler plugin as options. Each option can be given several times:

```sbt
scalacOptions ++= Seq(
  "-P:scaladoc-compiler-plugin:include:path:**/model/**",
  "-P:scaladoc-compiler-plugin:include:class:com.acme.api.*",
  "-P:scaladoc-compiler-plugin:exclude:path:**/generated/**",
  "-P:scaladoc-compiler-plugin:exclude:class:com.acme.**.internal.*"
)
```

| Option                    | Matches                                                                      |
|---------------------------|------------------------------------------------------------------------------|
| `include:path:<glob>`     | source files whose path matches the glob                                     |
| `exclude:path:<glob>`     | the same, to leave out                                                       |
| `include:class:<pattern>` | classes whose fully-qualified name matches the pattern                       |
| `exclude:class:<pattern>` | the same, to leave out                                                       |

- Without any `include` option every class is included. With some, a class is included if its file matches an
  `include:path` or its name matches an `include:class`.
- An `exclude` always wins over an `include`.
- Path globs use [`java.nio` glob syntax](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-)
  and are matched against the absolute source path, so they usually start with `**/`.
- In class patterns, `*` matches within one name segment, `**` across segments, and `**.` zero or more packages
  (`com.acme.**.internal.*` matches `com.acme.internal.Secret` and `com.acme.a.b.internal.Secret`). Nested classes are
  written with dots: `com.acme.Outer.Inner`.
- A malformed option fails the compilation.
