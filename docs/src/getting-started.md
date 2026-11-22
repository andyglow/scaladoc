# Getting started

> Talk is cheap. Show me the code.
>
> — Linus Torvalds

There are three ways in: the sbt plugin and the Gradle plugin, which know what each kind of module needs, or adding
the dependencies by hand. The plugins are the shorter road, so they come first.

## With the sbt plugin

Add `sbt-scaladoc` to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "@VERSION@")
```

and enable it on the modules that need it:

```sbt
lazy val model = project
  .enablePlugins(ScaladocPlugin)
```

That's it. What happens next depends on which Scala the module speaks, because Scala 2 and Scala 3 keep doc comments
in very different places:

- **Scala 2** forgets doc comments once it's done compiling, so the plugin adds the @ref:[compiler plugin](compiler-plugin.md),
  which writes them into the bytecode before they're lost. It also adds `scaladoc-ast` and `scaladoc-parser`. A module
  that only reads scaladoc, and has none of its own worth keeping, can set `scaladocEmbed := false`.
- **Scala 3** keeps doc comments in its TASTy files, so no compiler plugin is needed (and none exists). The plugin adds
  `scaladoc-ast`, `scaladoc-parser` and `-Yread-docs`, the option that tells the compiler to load those comments for
  @ref:[`Scaladoc.of`](extractor.md).
- **Scala.js** gets the `_sjs1` libraries. The compiler plugin runs inside scalac, which is a JVM program however its
  output is used, so the JVM artifact serves Scala.js modules too.

The libraries get the plugin's own version, and `scaladocVersion` overrides it. The other settings tune what the
compiler plugin embeds:

| Setting                  | Default  | What it does                                                         |
|--------------------------|----------|----------------------------------------------------------------------|
| `scaladocVersion`        | plugin's | the version of the libraries and the compiler plugin                 |
| `scaladocEmbed`          | `true`   | Scala 2: embed scaladoc into bytecode with the compiler plugin       |
| `scaladocIncludePaths`   | none     | Scala 2: embed only classes from source files matching these globs   |
| `scaladocExcludePaths`   | none     | Scala 2: don't embed classes from source files matching these globs  |
| `scaladocIncludeClasses` | none     | Scala 2: embed only classes whose name matches these patterns        |
| `scaladocExcludeClasses` | none     | Scala 2: don't embed classes whose name matches these patterns       |

The patterns are explained in @ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed).

## With the Gradle plugin

Gradle builds get the same setup from a Gradle plugin. Apply it next to Gradle's `scala` plugin:

```kotlin
plugins {
    scala
    id("io.github.andyglow.scaladoc") version "@VERSION@"
}
```

It reads the module's Scala version from its `scala-library` or `scala3-library_3` dependency, and then does what the
sbt plugin does: the compiler plugin and the two libraries on Scala 2, the libraries and `-Yread-docs` on Scala 3. On
Scala 2 the parser is only needed to compile, so it goes on `compileOnly` and `testImplementation`, not on the runtime
classpath.

A `scaladoc` block holds the settings, with the same meaning as the sbt plugin's:

```kotlin
scaladoc {
    version.set("@VERSION@")                       // the plugin's own by default
    embed.set(false)                               // Scala 2: read scaladoc, don't embed any
    excludeClasses.add("com.acme.**.internal.*")   // also includePaths, excludePaths and includeClasses
}
```

The plugin runs on Gradle 8.5 and later.

## By hand

The libraries are two ordinary dependencies:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "@VERSION@"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "@VERSION@"
```

To embed scaladoc into bytecode (Scala 2 only), add the compiler plugin as well. The parser is then only needed while
compiling, so it can stay out of the runtime classpath:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "@VERSION@")

libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "@VERSION@"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "@VERSION@" % Provided
```

On Scala 3, compile the modules that call `Scaladoc.of` with `-Yread-docs`:

```sbt
scalacOptions += "-Yread-docs"
```

On Scala.js, use `%%%` for `scaladoc-ast` and `scaladoc-parser`. The compiler plugin stays `%%`.

@@@ note

The groupId changed in 0.1.0, from `com.github.andyglow` to `io.github.andyglow`. Versions up to 0.0.14 stay
available under the old one. To upgrade from them, change the groupId along with the version.

@@@

## A first look

Before reaching for macros, it helps to see the parser on its own. Give it a comment, and it gives back what the
comment says:

```scala mdoc
import scaladoc._

val parsed = Scaladoc.fromString(
  """/** Adds two numbers.
    |  *
    |  * @param a the first one
    |  * @param b the second one
    |  * @return their sum
    |  */""".stripMargin)

parsed.map(_.textReturns)
```

The result is an `Either`, because not every string is a doc comment. The `Right` holds a `Scaladoc`: a list of tags,
each with its markup. The rest of this guide walks through that structure. @ref:[Comment syntax](syntax.md) covers what
you can write, @ref:[The model](model.md) what you get back, and @ref:[Scaladoc.of](extractor.md) how to get it without
having the comment as a string at all.
