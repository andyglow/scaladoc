# Getting started

> Talk is cheap. Show me the code.
>
> — Linus Torvalds

A module plays one of two parts, sometimes both. It **publishes** its scaladoc, keeping the doc comments of its classes
readable after it's compiled. Or it **reads** scaladoc, calling @ref:[`Scaladoc.of`](extractor.md) to get it. In a
typical project, a module of models publishes, and the module that derives schemas from them reads.

Each part needs something different, and what that is depends on the Scala version, because Scala 2 and Scala 3 keep
doc comments in very different places:

|                | Scala 2                                                               | Scala 3                          |
|----------------|-----------------------------------------------------------------------|----------------------------------|
| **Publishing** | the @ref:[compiler plugin](compiler-plugin.md), `scaladoc-annotation` | nothing                          |
| **Reading**    | `scaladoc-parser`                                                     | `scaladoc-parser`, `-Yread-docs` |

Scala 2 forgets doc comments once it's done compiling, so a publishing module needs the compiler plugin, which writes
them into the bytecode before they're lost, in an annotation. The annotation lives in `scaladoc-annotation`, a jar with
one class and no dependencies, and the publishing module depends on it: its classes refer to the annotation, and
whatever uses them gets the jar along, the way it gets `scala-library`. Scala 2 would do without it, but Scala 3
doesn't compile against such classes when it's missing, so a Scala 3 project couldn't use a 2.13 library published
this way. Scala 3 keeps doc comments in its TASTy files on its own; a reading module needs `-Yread-docs`, the option
that tells the compiler to load them.

A reading module calls `Scaladoc.of`, so it depends on `scaladoc-parser` like on any library it calls; `scaladoc-core`,
the model, comes with it. The rest is compiler setup, which is where the sbt and Gradle plugins help: they add the
compiler plugin, with `scaladoc-annotation`, on Scala 2 and `-Yread-docs` on Scala 3. Maven gets a few lines of POM.

## sbt

### The sbt plugin

Add `sbt-scaladoc` to `project/plugins.sbt`:

```sbt
addSbtPlugin("io.github.andyglow" % "sbt-scaladoc" % "@VERSION@")
```

and enable it on the modules whose compiler it should set up: those that publish on Scala 2, and those that read on
Scala 3. A module that reads scaladoc adds the parser itself:

```sbt
lazy val model = project
  .enablePlugins(ScaladocPlugin)            // publishes its scaladoc

lazy val schemas = project
  .dependsOn(model)
  .enablePlugins(ScaladocPlugin)            // on Scala 3, for -Yread-docs; a Scala 2 reader doesn't need it
  .settings(
    // reads it with Scaladoc.of; scaladocVersion keeps the parser in step with the plugin
    libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % scaladocVersion.value
  )
```

On Scala 2, a module the plugin is enabled on embeds its scaladoc, even one that only reads, as when it's cross-built
for Scala 3 and keeps the plugin for that. It does no harm: a few annotations in its bytecode. A module that shouldn't
embed any doesn't enable the plugin.

On Scala.js, the parser is the `_sjs1` artifact: on sbt 2, `%%` picks it by itself in a Scala.js module; on sbt 1, use
`%%%`. The compiler plugin runs inside scalac, which is a JVM program however its output is used, so the plugin adds
the JVM artifact to Scala.js modules too.

The compiler plugin gets the sbt plugin's own version, and `scaladocVersion` overrides it. The other settings tune what
the compiler plugin embeds:

| Setting                  | Default  | What it does                                                             |
|--------------------------|----------|--------------------------------------------------------------------------|
| `scaladocVersion`        | plugin's | the version of the compiler plugin                                       |
| `scaladocIncludePaths`   | none     | Scala 2: embed only classes from source files matching these globs       |
| `scaladocExcludePaths`   | none     | Scala 2: don't embed classes from source files matching these globs      |
| `scaladocIncludeClasses` | none     | Scala 2: embed only classes whose name matches these patterns            |
| `scaladocExcludeClasses` | none     | Scala 2: don't embed classes whose name matches these patterns           |

The patterns are explained in @ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed).

### By hand

A Scala 2 module that publishes its scaladoc needs the compiler plugin, and the annotation it embeds scaladoc in:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "@VERSION@")
libraryDependencies += "io.github.andyglow" % "scaladoc-annotation" % "@VERSION@"   // one `%`: a Java jar
```

A module that reads scaladoc needs the parser, which brings `scaladoc-core` along:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % "@VERSION@"
```

and on Scala 3, `-Yread-docs` as well:

```sbt
scalacOptions += "-Yread-docs"
```

On Scala.js, the parser is the `_sjs1` artifact: `%%` picks it by itself on sbt 2, and sbt 1 needs `%%%`. The compiler
plugin is the JVM artifact, `%%` on both, and `scaladoc-annotation` the same Java jar everywhere.

## Gradle

Apply the plugin next to Gradle's `scala` plugin:

```kotlin
plugins {
    scala
    id("io.github.andyglow.scaladoc") version "@VERSION@"
}
```

It reads the module's Scala version from its `scala-library` or `scala3-library_3` dependency and sets the compiler
up for it: the compiler plugin on Scala 2, `-Yread-docs` on Scala 3. On Scala 2 it also adds `scaladoc-annotation`,
and exposes it to the module's consumers, as `api` would, published POMs included. As with sbt, it belongs on the modules that
publish on Scala 2 and those that read on Scala 3. A `scaladoc` block holds the settings, which mean what the sbt
plugin's do:

```kotlin
scaladoc {
    excludeClasses.add("com.acme.**.internal.*")   // also includePaths, excludePaths and includeClasses
    version.set("@VERSION@")                       // of the compiler plugin; the plugin's own by default
}
```

A module that reads scaladoc adds the parser itself:

```kotlin
dependencies {
    implementation("io.github.andyglow:scaladoc-parser_2.13:@VERSION@")
}
```

The plugin runs on Gradle 8.5 and later.

## Maven

Maven builds Scala with `scala-maven-plugin`, and what a module needs fits into its configuration. The examples below
are for Scala 2.13; for 2.12, change the `_2.13` suffixes.

**A Scala 2 module that publishes its scaladoc** needs the compiler plugin, and the annotation it embeds scaladoc in,
at the default `compile` scope, so that it reaches the module's consumers:

```xml
<dependencies>
  <dependency>
    <groupId>io.github.andyglow</groupId>
    <artifactId>scaladoc-annotation</artifactId>
    <version>@VERSION@</version>
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
@ref:[Choosing what to embed](compiler-plugin.md#choosing-what-to-embed). A Scala 3 module that publishes needs
nothing at all.

**A module that reads scaladoc** needs the parser, which brings `scaladoc-core` along:

```xml
<dependency>
  <groupId>io.github.andyglow</groupId>
  <artifactId>scaladoc-parser_2.13</artifactId>
  <version>@VERSION@</version>
</dependency>
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
see [modules/tests/maven](https://github.com/andyglow/scaladoc/tree/master/modules/tests/maven) in the repository.

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
