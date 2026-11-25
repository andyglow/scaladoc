# Migrating

> No man ever steps in the same river twice, for it's not the same river and he's not the same man.
>
> — attributed to Heraclitus

## To 0.6

**On Scala 3, the libraries need Scala 3.9 and Java 17.** They are built with Scala 3.9, the LTS after 3.3, and a
project on Scala 3.3 can't read what a newer Scala compiled, so a Scala 3 project moves to 3.9 or stays on 0.5. Scala
3.9 targets Java 17 and up. On Scala 2 nothing changes: Scala 2.12 and 2.13, and Java 8.

**On Scala 3, `Scaladoc.of` reads doc comments as Markdown**, Scala 3's default syntax, where it read them as wiki
before. A codebase that writes its comments in wiki syntax, as with scaladoc's `-comment-syntax wiki`, says so with a
given in scope at the call site, or a comment with a tag, as in Scala 3's scaladoc:

```scala
import scaladoc.syntax.wiki.given    // every Scaladoc.of in scope reads wiki
```

```scala
/** Some '''bold''' text.
  *
  * @syntax wiki
  */
```

`fromString` and `fromDocstring` take the syntax as a parameter, `CommentSyntax.Wiki` by default, and honor `@syntax`
too. On Scala 2, `Scaladoc.of` reads wiki, Scala 2's only syntax, as before.

**`Scaladoc.of` expands the defines of parents and enclosing classes**, as scaladoc does, where it left `$name` as
written unless the comment itself defined it. See @ref:[Macros](syntax.md#macros).

**On Scala 3, `Scaladoc.of` also reads the scaladoc of Scala 2.13 classes**, that a library published with the
compiler plugin embedded in them, in wiki syntax. It returned `None` for them before.

The model grows the nodes Markdown needs (see @ref:[The model](model.md#markup)): `Strikethrough`, `Image`,
`LineBreak`, `Checkbox` and `FootnoteReference` among the spans, `BlockQuote` and `Footnote` among the blocks. Only
Markdown comments produce them, so code that walks wiki comments sees nothing new, but a `match` over `Markup` that
lists every node gets exhaustivity warnings until it handles them.

**`CodeBlock` has a language:** `CodeBlock(value, language)`, where `language` is what a Markdown fence names, like
`scala` in ```` ```scala ````, and `None` otherwise. Building one is unchanged, `CodeBlock("val x = 1")` still
compiles, but a pattern needs the second field:

```scala
// 0.5
case CodeBlock(code)    => code

// 0.6
case CodeBlock(code, _) => code
```

## To 0.5.1

The sbt and Gradle plugins set up the compiler and nothing else: the compiler plugin on Scala 2, and `-Yread-docs` on
Scala 3, now on every Scala 3 module they're enabled on. They no longer add libraries. A module that reads scaladoc
depends on `scaladoc-parser` itself, like on any library it calls; `scaladoc-core` comes with it:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-parser" % scaladocVersion.value   // sbt
```

```kotlin
implementation("io.github.andyglow:scaladoc-parser_2.13:@VERSION@")                        // Gradle
```

`scaladocRead` and Gradle's `read`, added in 0.5.0, are deprecated: they still add the libraries, with a warning, and
go away in 0.6.0. Replace them with the dependency above.

**A module that embeds scaladoc depends on `scaladoc-annotation`.** In 0.5.0 it got just the compiler plugin, which
broke its Scala 3 consumers: Scala 3 doesn't compile against classes that refer to the annotation without its jar,
and fails with "Bad symbolic reference … refers to ScaladocCarrier". The sbt and Gradle plugins now add it on Scala 2;
by hand, and in Maven, add it at compile scope:

```sbt
libraryDependencies += "io.github.andyglow" % "scaladoc-annotation" % "@VERSION@"
```

Until a library published with 0.5.0 is republished, a Scala 3 project that uses it adds the same dependency itself.
Versions before 0.5.0 aren't affected: there the plugins gave publishers `scaladoc-ast`, which held the annotation then.

`scaladocEmbed` and Gradle's `embed` are deprecated too, and go away in 0.6.0. With the libraries gone, a module that
doesn't embed gets nothing from the plugin, so it doesn't enable it. Until then, turning `embed` off still leaves the
compiler plugin out, with a deprecation warning. On Scala 3, where `embed` never did anything, a module that reads keeps
the plugin for `-Yread-docs` and drops the setting.

## To 0.5

0.5 gives a module only what its part needs. A module that publishes its scaladoc no longer gets the scaladoc
libraries along with the compiler plugin, and a module that reads it asks for them.

**The sbt and Gradle plugins** publish by default, as before, but now add no libraries for that: on Scala 2 just the
compiler plugin (0.5.1 adds `scaladoc-annotation` back, see above), on Scala 3 nothing. Reading is opt-in. In 0.5.0, a module that calls `Scaladoc.of` sets (see 0.5.1 above, which replaces this with a plain
dependency):

```sbt
scaladocRead := true          // sbt
```

```kotlin
scaladoc { read.set(true) }   // Gradle
```

Without it, a module that reads gets "not found: value Scaladoc" when compiling, and setting it fixes that. A module
that only reads could also turn publishing off, with `scaladocEmbed := false` or `embed.set(false)` (deprecated in
0.5.1).

**By hand**, a Scala 2 module that publishes can drop `scaladoc-ast` (now `scaladoc-core`) and `scaladoc-parser`, and
keep just the compiler plugin. A Scala 3 module that publishes needs nothing, and `-Yread-docs` belongs only to modules that read.

**`scaladoc-ast` is now `scaladoc-core`.** It holds `Scaladoc` itself, the model of tags and markup, and the helpers
like `textParams` and `summary`, which is more than a syntax tree. Only the artifact's name changed; the package is
`scaladoc` as before, so no code changes. The plugins add the new name themselves. By hand, rename the dependency:

```sbt
libraryDependencies += "io.github.andyglow" %% "scaladoc-core" % "@VERSION@"   // was scaladoc-ast
```

**`scaladoc-annotation`** is a new jar with just the `ScaladocCarrier` annotation, for anyone who reads embedded
scaladoc without this library; see @ref:[Reading embedded scaladoc without this library](compiler-plugin.md#reading-embedded-scaladoc-without-this-library).
The annotation's name and package haven't changed, so classes compiled with earlier versions read as before.
`scaladoc-parser` depends on it on Scala 2, so readers don't need to add it.

## To 0.4

0.4 is the release where the parser learned the rest of scaladoc's syntax: nested markup, link titles, lists, tables,
rules, HTML, javadoc's inline tags and `@define`. A model that couldn't hold those had to grow, and code that takes it
apart needs a few changes. Code that only builds markup, or only reads text through the `text*` helpers, mostly
doesn't.

### Spans hold spans

`Italic`, `Bold`, `Underline`, `Superscript` and `Subscript` held a `String`. They now hold a `List[Span]`, so markup
can nest. Building one from a string still works, through a shorthand on each companion:

```scala
Bold("text")                           // still compiles, now Bold(List(PlainText("text")))
```

Pattern matches on the string need updating:

```scala
// 0.3
case Bold(text)        => text
// 0.4
case b @ Bold(spans)   => b.trimmed.plainString     // or walk the spans
```

`Monospace` still holds a `String`: code is taken literally.

### Headings hold spans

`Heading(level, text: String)` is now `Heading(level, spans: List[Span])`, so a title can have markup.
`Heading(One, "Title")` and `Heading(1, "Title")` still build one. A heading's title is also trimmed now: `= Title =`
gives `"Title"`, not `" Title "`.

### Links have a target and a title

`Link(value: String)` held whatever was between the brackets, so `[[https://x.y Site]]` gave
`Link("https://x.y Site")`. It's now `Link(target: String, title: List[Span])`, and that link gives
`Link("https://x.y", List(PlainText("Site")))`. `Link("scala.Option")` still builds one without a title.

### New nodes

Code that matches on `Markup` exhaustively will hear from the compiler about these:

- `UnorderedList` and `OrderedList`, whose items are `Document`s
- `Table`, with its rows and column alignments
- `HorizontalRule`
- `HtmlTag`, a span for HTML tags the model has no node for

### Text that changed

The parser now understands more, so some comments that came back as plain text before don't anymore:

- a line like `  - item` is a list item, and `| a | b |` lines with a delimiter row are a table
- `<b>`, `<code>`, `&lt;` and the like are markup and characters, not text
- `{@link X}` is a link, `{@code x}` monospace
- `$name` is replaced when the comment defines `name` with `@define`

### Fixes that change results

0.4 also fixes a number of things that 0.3 got wrong. They don't change the API, but they do change what some
comments parse to:

- `@return` is the returns tag, as in scaladoc. `@returns` still works
- a tag starts a line, so `foo@bar.com` is text; and nothing inside `{{{ }}}` is a tag
- `@throws java.io.IOException` keeps its full exception type
- `@deprecated` and `@throws X` are kept without text
- the `*` border is optional
- an unclosed marker, like the `^` of `x^2`, is text instead of an error that loses the comment
- one space after the border belongs to the border, and text is always in a `Paragraph`
- code blocks lose the indentation all their lines share
- `Tag.Description`'s field is `markup`; the misspelled `makrup` stays as a deprecated alias
