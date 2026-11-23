# Migrating

> No man ever steps in the same river twice, for it's not the same river and he's not the same man.
>
> — attributed to Heraclitus

## To 0.5

0.5 gives a module only what its part needs. A module that publishes its scaladoc no longer gets the scaladoc
libraries along with the compiler plugin, and a module that reads it asks for them.

**The sbt and Gradle plugins** publish by default, as before, but now add no libraries for that: on Scala 2 just the
compiler plugin, on Scala 3 nothing. Reading is opt-in. A module that calls `Scaladoc.of` sets:

```sbt
scaladocRead := true          // sbt
```

```kotlin
scaladoc { read.set(true) }   // Gradle
```

Without it, a module that reads gets "not found: value Scaladoc" when compiling, and setting it fixes that. A module
that only reads can also turn publishing off, with `scaladocEmbed := false` or `embed.set(false)`.

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
