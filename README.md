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
- **a parser** that turns comment text into the model. It reads scaladoc's wiki markup, HTML and javadoc's inline
  tags, on the JVM and on Scala.js
- **`Scaladoc.of[T]`**, the scaladoc of a type at compile time, for macros and type class derivation
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
```

## Getting started

Add the sbt plugin to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "0.4.0")
```

and enable it on the modules that need it:

```sbt
lazy val model = project.enablePlugins(ScaladocPlugin)
```

It sets each module up for its Scala version: the compiler plugin on Scala 2, `-Yread-docs` on Scala 3, the `_sjs1`
libraries on Scala.js.

Gradle builds have a plugin that does the same, next to Gradle's `scala` plugin:

```kotlin
plugins {
    scala
    id("io.github.andyglow.scaladoc") version "0.4.0"
}
```

To add the dependencies by hand instead, see [Getting started](https://andyglow.github.io/scaladoc/getting-started.html).

| Module                     | Scala 2.12 | Scala 2.13 | Scala 3 (3.3+) | Scala.js 1.x |
|----------------------------|:----------:|:----------:|:--------------:|:------------:|
| `scaladoc-ast`             |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-parser`          |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-compiler-plugin` |     ✓      |     ✓      |       –        |   JVM one    |

`sbt-scaladoc` works with sbt 1.5.8+ and sbt 2.x, the Gradle plugin with Gradle 8.5+. All artifacts target Java 8. The groupId is `io.github.andyglow`
from 0.1.0; versions up to 0.0.14 are under `com.github.andyglow`.

## Documentation

The [site](https://andyglow.github.io/scaladoc) covers the [comment syntax](https://andyglow.github.io/scaladoc/syntax.html),
[the model](https://andyglow.github.io/scaladoc/model.html), [Scaladoc.of](https://andyglow.github.io/scaladoc/extractor.html),
the [compiler plugin](https://andyglow.github.io/scaladoc/compiler-plugin.html) and
[migrating to 0.4](https://andyglow.github.io/scaladoc/migration.html). Its sources are in [docs/src](docs/src), and
every snippet in them is compiled and run when the site is built. To preview it locally:

```
sbt "++2.13.18 docs/previewSite"
```
