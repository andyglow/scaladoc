# Getting started

> Talk is cheap. Show me the code.
>
> — Linus Torvalds

A module plays one of two parts, sometimes both. It **publishes** its scaladoc, keeping the doc comments of its classes
readable after it's compiled. Or it **reads** scaladoc, calling @ref:[`Scaladoc.of`](extractor.md) to get it. In a
typical project, a module of models publishes, and the module that derives schemas from them reads.

Each part needs something different, and what that is depends on the Scala version, because Scala 2 and Scala 3 keep
doc comments in very different places:

|                  | Scala 2                                                    | Scala 3                                   |
|------------------|------------------------------------------------------------|-------------------------------------------|
| **Publishing**   | the @ref:[compiler plugin](compiler-plugin.md), nothing else | nothing                                 |
| **Reading**      | `scaladoc-ast` and `scaladoc-parser`                       | the same, and `-Yread-docs`               |

Scala 2 forgets doc comments once it's done compiling, so a publishing module needs the compiler plugin, which writes
them into the bytecode before they're lost. The plugin's jar has everything it needs, so the module needs no library
for it. Scala 3 keeps doc comments in its TASTy files on its own; a reading module needs `-Yread-docs`, the option that
tells the compiler to load them.

sbt and Gradle have plugins that set this up for you. Maven gets a few lines of POM.

## sbt

### The sbt plugin

Add `sbt-scaladoc` to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "@VERSION@")
```

and enable it on the modules that publish or read scaladoc. A module publishes by default, and one that reads says so:

```sbt
lazy val model = project
  .enablePlugins(ScaladocPlugin)            // publishes its scaladoc

lazy val schemas = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)
  .settings(
    scaladocEmbed := false,                 // has no scaladoc of its own to keep
    scaladocRead  := true                   // reads it with Scaladoc.of
  )
```

On Scala.js, a reading module gets the `_sjs1` libraries. The compiler plugin runs inside scalac, which is a JVM
program however its output is used, so the JVM artifact serves Scala.js modules too.

The libraries get the plugin's own version, and `scaladocVersion` overrides it. The other settings tune what the
compiler plugin embeds:

| Setting                  | Default  | What it does                                                             |
|--------------------------|----------|--------------------------------------------------------------------------|
| `scaladocEmbed`          | `true`   | publish: Scala 2, embed scaladoc into bytecode with the compiler plugin  |
| `scaladocRead`           | `false`  | read: add the libraries, and `-Yread-docs` on Scala 3                    |
| `scaladocVersion`        | plugin's | the version of the libraries and the compiler plugin                     |
| `scaladocIncludePaths`   | none     | Scala 2: embed only classes from source files matching these globs       |
| `scaladocExcludePaths`   | none     | Scala 2: don't embed classes from source files matching these globs      |
| `scaladocIncludeClasses` | none     | Scala 2: embed only classes whose name matches these patterns            |
| `scaladocExcludeClasses` | none     | Scala 2: don't embed classes whose name matches these patterns           |

The patterns are explained in @ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed).

### By hand

A Scala 2 module that publishes its scaladoc needs only the compiler plugin:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "@VERSION@")
```

A module that reads scaladoc needs the two libraries:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-ast"    % "@VERSION@"
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "@VERSION@"
```

and on Scala 3, `-Yread-docs` as well:

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
version needs. Like the sbt plugin, it publishes the module's scaladoc by default, and reads when asked to, in a
`scaladoc` block whose settings mean what the sbt plugin's do:

```kotlin
scaladoc {
    embed.set(false)                               // publish: Scala 2, embed scaladoc (true by default)
    read.set(true)                                 // read it with Scaladoc.of (false by default)
    excludeClasses.add("com.acme.**.internal.*")   // also includePaths, excludePaths and includeClasses
    version.set("@VERSION@")                       // the plugin's own by default
}
```

The plugin runs on Gradle 8.5 and later.

## Maven

Maven builds Scala with `scala-maven-plugin`, and what a module needs fits into its configuration. The examples below
are for Scala 2.13; for 2.12, change the `_2.13` suffixes.

**A Scala 2 module that publishes its scaladoc** needs only the compiler plugin:

```xml
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
@ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed). A Scala 3 module that publishes needs
nothing at all.

**A module that reads scaladoc** needs the two libraries:

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
  </dependency>
</dependencies>
```

On Scala 3, that's with the `_3` suffix, and `-Yread-docs` as well:

```xml
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
