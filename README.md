# Scaladoc

[![CI](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/andyglow/scaladoc/branch/master/graph/badge.svg)](https://codecov.io/gh/andyglow/scaladoc)
[![scala 2.12](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_2.12?label=scala%202.12)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_2.12)
[![scala 2.13](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_2.13?label=scala%202.13)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_2.13)
[![scala 3](https://img.shields.io/maven-central/v/io.github.andyglow/scaladoc-parser_3?label=scala%203)](https://central.sonatype.com/artifact/io.github.andyglow/scaladoc-parser_3)

**Documentation: https://andyglow.github.io/scaladoc**

Every Scala codebase carries a second program, written in prose: the doc comments that say what a field holds and
what a parameter is for. This library puts that prose to work, for tools like JSON Schema, Avro or OpenAPI
generators that can use it for titles and descriptions:

- **a model** of scaladoc's tags and markup: paragraphs, headings, lists, tables, code, links and the rest
- **a parser** that turns comment text into the model. It reads scaladoc's wiki markup and Markdown, as Scala 3's
  scaladoc does, with HTML and javadoc's inline tags, on the JVM and on Scala.js
- **`Scaladoc.of[T]`**, the scaladoc of a type at compile time, for macros and type class derivation
- **`Typedoc.of[T]`** (from 0.6), the scaladoc of a sealed hierarchy or an enum with its variants', and of a case class
  with its fields', as a tree
- **a compiler plugin** (Scala 2) that embeds scaladoc into bytecode, so it can be read from jars

```scala
import scaladoc._

/** A user of the app.
  *
  * @param name the name shown to other users
  * @param age  how old they are, in years
  */
case class User(name: String, age: Int)

Scaladoc.of[User].map(_.textParams)
// Some(Map("name" -> "the name shown to other users", "age" -> "how old they are, in years"))

/** What a user may do. */
sealed trait Role

/** Can do anything, invite others included. */
case object Admin extends Role

/** Can read and comment.
  *
  * @param team the team they're in
  */
case class Member(team: String) extends Role

def summaries(t: Typedoc): List[(String, Option[String])] = t match {
  case Typedoc.Sum(name, doc, variants) => (name -> doc.flatMap(_.textSummary)) :: variants.flatMap(summaries)
  case other                            => List(other.name -> other.doc.flatMap(_.textSummary))
}

summaries(Typedoc.of[Role])
// List(("Role", Some("What a user may do.")), ("Admin", Some("Can do anything, invite others included.")),
//   ("Member", Some("Can read and comment.")))
```

## Getting started

Add the sbt plugin to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "0.5.0")
```

and enable it on the modules that need it:

```sbt
lazy val model = project.enablePlugins(ScaladocPlugin)
```

The plugin sets the module's compiler up: on Scala 2 it adds the compiler plugin, which keeps the module's scaladoc in
its bytecode, with `scaladoc-annotation`, the one-class jar its classes then refer to; on Scala 3, which keeps scaladoc
in TASTy on its own, `-Xread-docs`. It adds no library for reading. A module that
reads scaladoc with `Scaladoc.of` or `Typedoc.of` depends on the parser, like on any library it calls:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "0.5.0"
```

Gradle builds have a plugin that does the same, next to Gradle's `scala` plugin:

```kotlin
plugins {
    scala
    id("io.github.andyglow.scaladoc") version "0.5.0"
}
```

Maven builds add a few lines to `scala-maven-plugin`'s configuration, and scala-cli projects a few `using`
directives. For those, and for adding the dependencies by hand in sbt, see [Getting started](https://andyglow.github.io/scaladoc/getting-started.html).

| Module                     | Scala 2.12 | Scala 2.13 | Scala 3 (3.9+) | Scala.js 1.x |
|----------------------------|:----------:|:----------:|:--------------:|:------------:|
| `scaladoc-core`            |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-parser`          |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-compiler-plugin` |     ✓      |     ✓      |       –        |   JVM one    |
| `scaladoc-annotation`      |     ✓      |     ✓      |       ✓        |      ✓       |

`scaladoc-core` was `scaladoc-ast` up to 0.4. `scaladoc-annotation` (from 0.5.0) is a single Java jar, with no Scala suffix, that holds just the annotation embedded
scaladoc is kept in, for tools that read it their own way. `sbt-scaladoc` works with sbt 1.5.8+ and sbt 2.x, the
Gradle plugin with Gradle 8.5+. Scala 2 artifacts target Java 8, Scala 3 ones Java 17 (Scala 3.9 needs it). The groupId is `io.github.andyglow` from 0.1.0; versions
up to 0.0.14 are under `com.github.andyglow`.

## Documentation

The [site](https://andyglow.github.io/scaladoc) covers the [comment syntax](https://andyglow.github.io/scaladoc/syntax.html),
[the model](https://andyglow.github.io/scaladoc/model.html),
[Scaladoc.of and Typedoc.of](https://andyglow.github.io/scaladoc/extractor.html), the [compiler plugin](https://andyglow.github.io/scaladoc/compiler-plugin.html) and
[migrating](https://andyglow.github.io/scaladoc/migration.html). Its sources are in [docs/src](docs/src), and
every snippet in them is compiled and run when the site is built. To preview it locally:

```
sbt "++2.13.18 docs/previewSite"
```
