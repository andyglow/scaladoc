# Scaladoc

[![CI](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml)
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

All artifacts target Java 8 and work on any later JDK. Scala 2.11 is supported up to version 0.0.14.

> **The groupId changed in 0.1.0** from `com.github.andyglow` to `io.github.andyglow`. Versions up to 0.0.14 stay
> available under `com.github.andyglow`. To upgrade, change the groupId along with the version.

## Usage

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "0.1.0"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "0.1.0"
```

To embed scaladocs into bytecode (Scala 2 only), also add the compiler plugin:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "0.1.0")

libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "0.1.0"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "0.1.0" % Provided
```

## Scaladoc AST
The model gives a detailed view of a description. It covers the scaladoc tags and also recognizes markdown markup
in the tags that allow it.

```
Span     = PlainText (String)
         | Monospace (String)
         | Italic (String)
         | Bold (String)
         | Underline (String)
         | Superscript (String)
         | Subscript (String)
         | Link (String)
         | CodeBlock (String)

Markup   = Span
         | Heading (Level String)
         | Paragraph (List Span)
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
