# Compiler plugin

> The palest ink is better than the best memory.
>
> — Chinese proverb

The Scala 2 compiler has a short memory. It reads every doc comment while it compiles a file, then lets go of it, and
nothing of the comment reaches the bytecode. A tool that looks at your classes later, from another module or out of a
jar, finds the types intact and the prose gone.

The compiler plugin writes the prose down before it's forgotten. For every documented class and object, it:

- validates the comment. One the parser can't read, like one with a misaligned border, is reported as a warning in
  the `scaladoc` category and isn't embedded. `-Xfatal-warnings`, or `-Wconf:cat=scaladoc:e` on 2.13, turns it into an error
- packs the comment into a `@ScaladocCarrier` annotation on the class, which stays in the bytecode. For an object,
  that's its class, `Obj$`, and the class of its static forwarders, `Obj`, when it has no companion class

@ref:[`Scaladoc.of` and `Typedoc.of`](extractor.md) find the annotation, so they work for classes and objects from any
module or jar compiled with the plugin. The plugin is for Scala 2.12 and 2.13. Scala 3 doesn't need it, since it keeps
comments in TASTy on its own.

The @ref:[sbt](getting-started.md#the-sbt-plugin) and @ref:[Gradle](getting-started.md#gradle) plugins add it on Scala 2
modules, and @ref:[Maven](getting-started.md#maven) builds list it in `scala-maven-plugin`'s `compilerPlugins`. In sbt,
by hand:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "@VERSION@")
libraryDependencies += "io.github.andyglow" % "scaladoc-annotation" % "@VERSION@"
```

The plugin's jar has everything it needs to run. The module still depends on `scaladoc-annotation`, which holds the
annotation and nothing else, because its classes refer to the annotation in their Scala signatures. Scala 2 compiles
against them without it, and the JVM runs them without it. Scala 3 doesn't: a Scala 3 module that uses them fails
with "Bad symbolic reference … refers to ScaladocCarrier", even if it never reads any scaladoc. With the dependency at
compile scope, the jar reaches every consumer, like `scala-library` does.

## Reading embedded scaladoc without this library

The annotation is a public, documented format, and this library's macros are just one way to read it. It's a plain Java
annotation, `scaladoc.annotation.ScaladocCarrier`, with runtime retention:

| Element  | Holds                                                                                  |
|----------|----------------------------------------------------------------------------------------|
| `text()` | the doc comment as it is in the source                                                 |
| `tags()` | whether `text()` has the comment's `/**` and `*/` markers and `*` border (`true`), or is just its content |

It lives in a jar of its own, `scaladoc-annotation`, which has no dependencies and no Scala version suffix. A tool that
reads embedded scaladoc its own way, at runtime with reflection or in any JVM language, needs only that jar:

```sbt
libraryDependencies += "io.github.andyglow" % "scaladoc-annotation" % "@VERSION@"
```

```java
ScaladocCarrier carrier = User.class.getAnnotation(ScaladocCarrier.class);
String comment = carrier == null ? null : carrier.text();   // null: no scaladoc was embedded
```

The text is then yours to process, with this library's parser (`Scaladoc.fromString(text, hasTags = carrier.tags())`)
or anything else. Tools that read class files directly, without loading them, find the annotation by its name and
need no jar at all.

The annotation's class doesn't have to be present wherever the annotated classes are used: the JVM loads and runs
them without it, and Java reflection then just leaves the annotation out.

## Choosing what to embed

By default the plugin embeds the scaladoc of every documented class and object. That's often more than needed, and a comment
meant for the team may not be meant for every jar. Include and exclude patterns narrow it down. With `sbt-scaladoc`
they're settings:

```sbt
scaladocIncludePaths   := Seq("**/model/**")
scaladocIncludeClasses := Seq("com.acme.api.*")
scaladocExcludePaths   := Seq("**/generated/**")
scaladocExcludeClasses := Seq("com.acme.**.internal.*")
```

Without it, they're options of the compiler plugin, and each can be given several times:

```sbt
scalacOptions ++= Seq(
  "-P:scaladoc-compiler-plugin:include:path:**/model/**",
  "-P:scaladoc-compiler-plugin:include:class:com.acme.api.*",
  "-P:scaladoc-compiler-plugin:exclude:path:**/generated/**",
  "-P:scaladoc-compiler-plugin:exclude:class:com.acme.**.internal.*"
)
```

| Option                    | Matches                                               |
|---------------------------|-------------------------------------------------------|
| `include:path:<glob>`     | source files whose path matches the glob              |
| `exclude:path:<glob>`     | the same, to leave out                                |
| `include:class:<pattern>` | classes and objects whose fully qualified name matches the pattern |
| `exclude:class:<pattern>` | the same, to leave out                                |

The rules, in the order they're applied:

- Without any `include`, every class is included. With some, a class is included if its file matches an
  `include:path` or its name matches an `include:class`.
- An `exclude` always wins over an `include`.
- Path globs use [`java.nio` glob syntax](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-)
  and are matched against the absolute path of the source file, so they usually start with `**/`.
- In class patterns, `*` matches within one segment of the name, `**` across segments, and `**.` zero or more
  packages: `com.acme.**.internal.*` matches both `com.acme.internal.Secret` and `com.acme.a.b.internal.Secret`.
  Nested classes are written with dots, as in `com.acme.Outer.Inner`, and objects are matched by their name, like
  classes: `com.acme.Registry`, not `com.acme.Registry$`.
- A malformed option fails the compilation, rather than quietly embedding everything.
