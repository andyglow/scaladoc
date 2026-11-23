# Getting started

> Talk is cheap. Show me the code.
>
> — Linus Torvalds

Whatever builds your project, a module needs the same few things, and what they are depends on which Scala it speaks.
Scala 2 and Scala 3 keep doc comments in very different places:

- **Scala 2** forgets doc comments once it's done compiling. A module whose scaladoc should be readable later needs
  the @ref:[compiler plugin](compiler-plugin.md), which writes the comments into the bytecode before they're lost,
  plus `scaladoc-ast` at runtime and `scaladoc-parser` to compile. A module that only reads scaladoc, and has none of
  its own worth keeping, needs just the two libraries.
- **Scala 3** keeps doc comments in its TASTy files, so there's no compiler plugin (none is needed). A module needs
  `scaladoc-ast`, `scaladoc-parser` and `-Yread-docs`, the option that tells the compiler to load those comments for
  @ref:[`Scaladoc.of`](extractor.md).

sbt and Gradle have plugins that work this out for you. Maven gets a few lines of POM.

## sbt

### The sbt plugin

Add `sbt-scaladoc` to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "@VERSION@")
```

and enable it on the modules that need it:

```sbt
lazy val model = project
  .enablePlugins(ScaladocPlugin)
```

That's it. On Scala 2 the module gets the compiler plugin and the two libraries, on Scala 3 the libraries and
`-Yread-docs`. On Scala.js it gets the `_sjs1` libraries; the compiler plugin runs inside scalac, which is a JVM
program however its output is used, so the JVM artifact serves Scala.js modules too.

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

### By hand

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

## Gradle

Apply the plugin next to Gradle's `scala` plugin:

```kotlin
plugins {
    scala
    id("io.github.andyglow.scaladoc") version "@VERSION@"
}
```

It reads the module's Scala version from its `scala-library` or `scala3-library_3` dependency and adds what that
version needs. On Scala 2 the parser is only needed to compile, so it goes on `compileOnly` and `testImplementation`,
not on the runtime classpath.

A `scaladoc` block holds the settings, with the same meaning as the sbt plugin's:

```kotlin
scaladoc {
    version.set("@VERSION@")                       // the plugin's own by default
    embed.set(false)                               // Scala 2: read scaladoc, don't embed any
    excludeClasses.add("com.acme.**.internal.*")   // also includePaths, excludePaths and includeClasses
}
```

The plugin runs on Gradle 8.5 and later.

## Maven

Maven builds Scala with `scala-maven-plugin`, and what a module needs fits into its configuration. The examples below
are for Scala 2.13; for 2.12, change the `_2.13` suffixes.

**A Scala 2 module whose scaladoc gets embedded** needs the compiler plugin, `scaladoc-ast`, and `scaladoc-parser` to
compile:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.andyglow</groupId>
    <artifactId>scaladoc-ast_2.13</artifactId>
    <version>@VERSION@</version>
  </dependency>
  <dependency>
    <groupId>io.github.andyglow</groupId>
    <artifactId>scaladoc-parser_2.13</artifactId>
    <version>@VERSION@</version>
    <scope>provided</scope>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>net.alchim31.maven</groupId>
      <artifactId>scala-maven-plugin</artifactId>
      <configuration>
        <compilerPlugins>
          <compilerPlugin>
            <groupId>io.github.andyglow</groupId>
            <artifactId>scaladoc-compiler-plugin_2.13</artifactId>
            <version>@VERSION@</version>
          </compilerPlugin>
        </compilerPlugins>
        <args>
          <!-- optional: what to leave out, see "Choosing what to embed" -->
          <arg>-P:scaladoc-compiler-plugin:exclude:class:com.acme.**.internal.*</arg>
        </args>
      </configuration>
    </plugin>
  </plugins>
</build>
```

The include and exclude patterns are the compiler plugin's options, one `<arg>` each; see
@ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed).

**A Scala 2 module that only reads scaladoc** needs just the two libraries, both with the default scope.

**A Scala 3 module** needs the two libraries, with the `_3` suffix, and `-Yread-docs`:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.andyglow</groupId>
    <artifactId>scaladoc-ast_3</artifactId>
    <version>@VERSION@</version>
  </dependency>
  <dependency>
    <groupId>io.github.andyglow</groupId>
    <artifactId>scaladoc-parser_3</artifactId>
    <version>@VERSION@</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>net.alchim31.maven</groupId>
      <artifactId>scala-maven-plugin</artifactId>
      <configuration>
        <args>
          <arg>-Yread-docs</arg>
        </args>
      </configuration>
    </plugin>
  </plugins>
</build>
```

This setup is tested as a real Maven build, on Scala 2.13, 2.12 and 3, with `scala-maven-plugin` 4.10 and Maven 3.10:
see [maven-tests](https://github.com/andyglow/scaladoc/tree/master/maven-tests) in the repository.

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
