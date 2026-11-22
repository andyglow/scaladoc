# Compiler plugin

> The palest ink is better than the best memory.
>
> — Chinese proverb

The Scala 2 compiler has a short memory. It reads every doc comment while it compiles a file, then lets go of it, and
nothing of the comment reaches the bytecode. A tool that looks at your classes later, from another module or out of a
jar, finds the types intact and the prose gone.

The compiler plugin writes the prose down before it's forgotten. For every documented class, it:

- validates the comment. One the parser can't read, like one with a misaligned border, is reported as a warning in
  the `scaladoc` category and isn't embedded. `-Xfatal-warnings`, or `-Wconf:cat=scaladoc:e` on 2.13, turns it into an error
- packs the comment into a `@ScaladocCarrier` annotation on the class, which stays in the bytecode

@ref:[`Scaladoc.of`](extractor.md) finds the annotation, so it works for classes from any module or jar compiled with the
plugin. The plugin is for Scala 2.12 and 2.13. Scala 3 doesn't need it, since it keeps comments in TASTy on its own.

The @ref:[sbt plugin](getting-started.md#with-the-sbt-plugin) adds it on Scala 2 modules. By hand:

```sbt
addCompilerPlugin("io.github.andyglow" %% "scaladoc-compiler-plugin" % "@VERSION@")
```

## Choosing what to embed

By default the plugin embeds the scaladoc of every documented class. That's often more than needed, and a comment
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
| `include:class:<pattern>` | classes whose fully qualified name matches the pattern |
| `exclude:class:<pattern>` | the same, to leave out                                |

The rules, in the order they're applied:

- Without any `include`, every class is included. With some, a class is included if its file matches an
  `include:path` or its name matches an `include:class`.
- An `exclude` always wins over an `include`.
- Path globs use [`java.nio` glob syntax](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-)
  and are matched against the absolute path of the source file, so they usually start with `**/`.
- In class patterns, `*` matches within one segment of the name, `**` across segments, and `**.` zero or more
  packages: `com.acme.**.internal.*` matches both `com.acme.internal.Secret` and `com.acme.a.b.internal.Secret`.
  Nested classes are written with dots, as in `com.acme.Outer.Inner`.
- A malformed option fails the compilation, rather than quietly embedding everything.
