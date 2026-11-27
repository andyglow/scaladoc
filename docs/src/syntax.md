# Comment syntax

> The limits of my language mean the limits of my world.
>
> — Ludwig Wittgenstein, *Tractatus Logico-Philosophicus*

A doc comment looks like prose, but it's a small language with a grammar of its own. Scaladoc defined most of it:
the `*` border, the `@` tags, a wiki markup with `''italic''` and `[[links]]`. Over the years, comments picked up
some HTML and a few habits from javadoc along the way, and real codebases contain all of it, often in the same comment.

The parser reads all of it, and this page walks through what it understands, from the outside in: first the comment
as a whole, then its tags, then the markup inside them. That markup is scaladoc's wiki syntax, Scala 2's, unless the
comment is in Markdown, Scala 3's default, which the @ref:[last part](#markdown) is about. Every example on this page is compiled and run when the site
is built, so what you see after each snippet is what the parser really returns.

```scala mdoc:invisible
import scaladoc._
import scaladoc.Markup._

// the markup of a comment's description
def parse(comment: String): Markup =
  Scaladoc.fromString(comment).fold(e => throw e, _.descriptions.headOption.getOrElse(Document(Nil)))
```

## The comment

A doc comment starts with `/**` and ends with `*/`. In between, each line usually starts with a `*` border: aligned
under the second `*` of the opening marker in scaladoc's style, under the first in javadoc's. The border and one
space after it belong to the comment's frame, not to its text:

```scala mdoc
parse(
  """/** A comment in scaladoc's style,
    |  * the border under the second star.
    |  */""".stripMargin)

parse(
  """/**
    | * And one in javadoc's style.
    | */""".stripMargin)
```

The border is optional, as it is for scaladoc. A line without one is text from its first character on:

```scala mdoc
parse(
  """/** No borders here,
    |    just text.
    | */""".stripMargin)
```

Trailing whitespace and blank lines at the start and the end of a comment are dropped. What remains is text, and the
text is always made of blocks: here, a single paragraph.

## Tags

> Every piece of knowledge must have a single, unambiguous, authoritative representation within a system.
>
> — Andy Hunt and Dave Thomas, *The Pragmatic Programmer*

A tag starts a line with `@` and its name. Everything up to the next tag is its text, and the text before the first tag
is the description. An `@` in the middle of a line, as in an e-mail address or `@tailrec`, is just a character:

```scala mdoc
Scaladoc.fromString(
  """/** Sends mail to support@example.com, see `@tailrec`.
    |  *
    |  * @param to   who gets it
    |  * @param body what it says,
    |  *             over two lines
    |  * @throws java.io.IOException if the server is down
    |  * @return the message id
    |  */""".stripMargin).map(_.tags.map(_.productPrefix))
```

These are the tags the model knows by name. Any other one, like `@contentDiagram`, comes back as an `OtherTag` with
its name and text.

| Tag                                 | Model                        | Text            |
|-------------------------------------|------------------------------|-----------------|
| the text before the first tag       | `Description`                | markup          |
| `@constructor`                      | `Constructor`                | markup          |
| `@param name`                       | `Param(name, ...)`           | markup          |
| `@tparam name`                      | `TypeParam(name, ...)`       | markup          |
| `@return` (or `@returns`)           | `Returns`                    | markup          |
| `@throws type`                      | `Throws(type, ...)`          | markup          |
| `@see`, `@note`, `@example`         | `See`, `Note`, `Example`     | markup          |
| `@usecase`                          | `UseCase`                    | markup          |
| `@todo`, `@deprecated`, `@migration`| `Todo`, `Deprecated`, `Migration` | markup     |
| `@author`, `@version`, `@since`     | `Author`, `Version`, `Since` | plain text      |
| `@group id`                         | `Group(id)`                  | none            |
| `@groupname id`                     | `GroupName(id, ...)`         | plain text      |
| `@groupdesc id`                     | `GroupDescription(id, ...)`  | markup          |
| `@groupprio id n`                   | `GroupPriority(id, n)`       | a number        |
| `@documentable`, `@inheritdoc`      | `Documentable`, `InheritDoc` | none            |
| anything else                       | `OtherTag(name, ...)`        | markup          |

A tag without text is dropped, since it says nothing, with two exceptions that scaladoc makes as well:
`@deprecated` deprecates even without a reason, and `@throws IOException` names an exception even without saying
when.

## Inline markup

Inside a paragraph, a few markers change how text looks. They are scaladoc's wiki markup:

| Markup        | Model         |
|---------------|---------------|
| `` `code` ``  | `Monospace`   |
| `''italic''`  | `Italic`      |
| `'''bold'''`  | `Bold`        |
| `__underline__` | `Underline` |
| `^super^`     | `Superscript` |
| `,,sub,,`     | `Subscript`   |
| `[[link]]`    | `Link`        |

Markup nests, so bold text can contain italic and links, and so on all the way down. The text of a monospace span is
taken literally, which is what you want from code:

```scala mdoc
parse("/** '''Bold, with ''italic'' and [[scala.Option]] inside''', and `code with ''quotes''`. */")
```

> Be conservative in what you do, be liberal in what you accept from others.
>
> — Jon Postel, RFC 761

Comments are written by people, and people write `x^2` and forget a backtick. A marker that isn't closed within its
paragraph is therefore plain text, not an error that loses the whole comment:

```scala mdoc
parse("/** The cost grows like n^2, and `this backtick never closes. */")
```

HTML and javadoc tags are another matter: a `<b>` or a `{@code` that isn't closed in its paragraph is an error, since
nobody writes one by accident, and there's no telling where it was meant to end. The error shows how the text goes
from the tag:

```scala mdoc
Scaladoc.fromString("/** A <b>bold claim, never closed. */")
```

Markup nests at most 64 deep. Deeper, a marker is text, as it is without a closer, and a tag is an error.

## Links

A link holds a target and, optionally, a title. The target runs to the first whitespace and the rest is the title,
which can have markup of its own. The target is either an entity, like a class or a member, or a URL:

```scala mdoc
parse("/** See [[scala.Option]] and [[https://docs.scala-lang.org the ''Scala'' docs]]. */")
```

Member signatures can contain `]]` themselves. A link opened with more brackets closes with as many, which keeps
such a target in one piece:

```scala mdoc
parse("/** Overloads are told apart like [[[baz(x:List[List[A]])*]]]. */")
```

`Link.isUrl` tells the two kinds of target apart.

## Paragraphs and headings

A blank line ends a paragraph. A heading is a line of its own, wrapped in as many `=` on each side as its level, from
one to six:

```scala mdoc
parse(
  """/** = The `App` trait =
    |  *
    |  * First paragraph,
    |  * still the first.
    |  *
    |  * == Caveats ==
    |  * Second paragraph.
    |  */""".stripMargin)
```

A heading doesn't need a blank line before or after it, and its title can have markup too.

## Code blocks

Code goes between `{{{` and `}}}`. A code block can span blank lines, and loses the indentation that all of its lines
share, so the code reads the same however deeply the comment is nested:

```scala mdoc
parse(
  """/** For example:
    |  * {{{
    |  *   val x = 1
    |  *
    |  *   if (x > 0)
    |  *     println(x)
    |  * }}}
    |  */""".stripMargin)
```

Nothing inside a code block is a tag, so an example can show `@main` or `@tailrec` without the parser mistaking them
for the start of a `@main` tag.

## Lists

> The list is the origin of culture.
>
> — Umberto Eco

A list item is an indented line that starts with `-` for a bullet, or with `1.`, `i.`, `I.`, `a.` or `A.` for the
numbering styles. The indentation matters twice. Without it, a line that starts with `-` is just text. And more of it
nests a list under the item before it:

```scala mdoc
parse(
  """/** Steps:
    |  *
    |  *   1. Clone the repository
    |  *   1. Build it
    |  *     - on the JVM
    |  *     - on Scala.js
    |  *   1. Run the tests
    |  */""".stripMargin)
```

Each item is a document of its own, holding the item's text and the lists nested under it. An item's text continues
on the lines that follow, until the next item, a blank line or the end of the list. A blank line between items
doesn't end the list. A different numbering style at the same indentation starts a new list.

## Tables

Tables use pipes, a header row, and a delimiter row that sets each column's alignment with `:`:

```scala mdoc
parse(
  """/** Sizes:
    |  *
    |  * | Size | Price | Notes      |
    |  * |:-----|------:|:----------:|
    |  * | S    | 10    | ''cheap''  |
    |  * | M    | 15    |
    |  */""".stripMargin)
```

Cells hold inline markup, and `\|` is a pipe inside a cell. Rows are padded or cut to the width of the header. Lines
without a valid delimiter row aren't a table at all, and stay text.

## Horizontal rules

Four or more dashes on a line of their own make a rule:

```scala mdoc
parse("/** Above.\n  *\n  * ----\n  *\n  * Below.\n  */")
```

As in scaladoc, a rule starts a block, but doesn't interrupt a paragraph: `----` right under a line of text is text.

## HTML

Doc comments have always had HTML in them, often from the days when they were written for javadoc. The parser turns
it into the same model as the wiki markup, wherever the model has an equivalent:

- `<p>` and `<div>` break paragraphs, `<h1>` to `<h6>` are headings, `<li>` are list items, `<pre>` is a code block
- `<b>` and `<strong>` are bold, `<i>` and `<em>` italic, `<u>` underlined, `<sup>` and `<sub>` raised and lowered
- `<code>` and `<tt>` are monospace, `<a href="...">` is a link, `<br>` is a line break
- entities like `&lt;`, `&#60;` and `&nbsp;` are the characters they stand for
- `<!-- comments -->` go, with any tags in them

```scala mdoc
parse("/** <p>Returns a <code>List&lt;A&gt;</code> of <b>all</b> the <a href=\"https://example.com\">items</a>.</p> */")
```

The tags that become the model, from `<b>` to `<a href="...">`, have to be closed in their paragraph (see
@ref:[Inline markup](#inline-markup)). Other tags that scaladoc keeps, like `<span>` or `<table>`, come back as `HtmlTag` spans with the
tag as written, closed or not. A
`<` that isn't a tag at all, as in `List<String>`, is text. HTML inside `{{{` code blocks stays as it is, and so it does
in `<pre>`, except that `<code>` tags go and entities are decoded.

## Javadoc's inline tags

Javadoc's inline tags are understood too:

| Tag                       | Model                          |
|---------------------------|--------------------------------|
| `{@code x}`               | `Monospace`                    |
| `{@value x}`              | `Monospace`                    |
| `{@link X}`, `{@link X label}`         | `Link`, with the label as its title |
| `{@linkplain X}`, `{@linkplain X label}` | `Link`, with the label as its title |
| `{@literal x}`            | text                           |
| `{@docRoot}`              | nothing                        |

```scala mdoc
parse("/** Wraps a {@link java.util.Map} of {@code Map<K, {V}>}, see {@linkplain Foo#bar the bar}. */")
```

Braces inside a tag must balance, which is what lets `{@code Map<K, {V}>}` hold braces of its own, and a tag has to
be closed in its paragraph, in Markdown too. Others, like `{@inheritDoc}`, are text.

## Macros

> I have made this longer than usual because I have not had time to make it shorter.
>
> — Blaise Pascal, *Lettres provinciales*

When a phrase repeats across a comment, `@define` gives it a name. `$name` or `${name}` then stands for it anywhere in
the comment, and `\$` is a plain dollar sign:

```scala mdoc
Scaladoc.fromString(
  """/** Adds an element to the $coll.
    |  *
    |  * @define coll immutable list
    |  * @param x the element to add to the $coll
    |  */""".stripMargin).map(doc => (doc.textDescriptions, doc.textParams))
```

A define runs up to the next tag, can use other defines, and doesn't show up in the parsed comment. An unknown variable
stays as written.

A comment also uses the defines of its class's parents and enclosing classes, as scaladoc has it. Only the compiler
knows those classes, so that's `Scaladoc.of`'s doing: it looks a variable up in the comment itself, then in the base
classes in linearization order, and then in the enclosing classes and objects, the same way, outward. The first one
found wins, and a define is expanded where it's used, so a class can change what a parent's `$coll` means:

```scala
/** A collection of things.
  *
  * @define coll collection
  * @define adds Adds an element to this $coll.
  */
trait Coll

/** A list. $adds
  *
  * @define coll list
  */
class MyList extends Coll  // Scaladoc.of[MyList]: "A list. Adds an element to this list."
```

The parents' comments come from where `Scaladoc.of` finds any comment: TASTy on Scala 3, and on Scala 2 the source
when the parent is compiled in the same run, or the scaladoc the compiler plugin embedded in it. On Scala 3, a class's
self type counts when the class is compiled in the same run, since it's read from the class's definition. `fromString`
sees a single comment, so only its own defines.

## Markdown

> Simplicity is prerequisite for reliability.
>
> — Edsger W. Dijkstra

Scala 3's scaladoc reads doc comments as Markdown by default, and wiki syntax only when told to. The parser reads
both, and is told which one a comment is written in, since the same characters mean different things in the two:
`__x__` is underline in wiki and bold in Markdown, and `=x=` is a heading in one and text in the other.

`Scaladoc.fromString` and `fromDocstring` take the syntax as a parameter, wiki by default. A comment can also say it
for itself, as in Scala 3's scaladoc, with `@syntax wiki` or `@syntax markdown` on a line of its own; the tag isn't
kept in the parsed comment. @ref:[Scaladoc.of](extractor.md#which-syntax) picks the syntax by where it finds the
comment.

```scala mdoc:invisible
def parseMarkdown(comment: String): Markup =
  Scaladoc.fromString(comment, syntax = CommentSyntax.Markdown)
    .fold(e => throw e, _.descriptions.headOption.getOrElse(Document(Nil)))
```

```scala mdoc
parseMarkdown(
  """/** Some **bold**, `code` and a [link](https://scala-lang.org).
    |  *
    |  * - [x] a task, done
    |  * - [ ] and one to do
    |  */""".stripMargin)

Scaladoc.fromString(
  """/** Some '''bold''' text.
    |  *
    |  * @syntax wiki
    |  */""".stripMargin, syntax = CommentSyntax.Markdown).map(_.descriptions)
```

Both syntaxes give the same model, named for what the markup means, so tools that walk it work the same on either:
`'''bold'''` and `**bold**` are both `Bold`. Tags work the same in both, and only their text's markup differs. A few
nodes only come from Markdown, because wiki syntax can't write them: `BlockQuote`, `Strikethrough`, `Image`,
`LineBreak`, `Checkbox` and footnotes; see @ref:[The model](model.md#markup).

### What it reads

The Markdown is Scala 3's: [CommonMark](https://commonmark.org), with the extensions Scala 3's scaladoc turns on.

- **Blocks:** paragraphs, `#` and underlined headings, fenced (```` ``` ````, `~~~`, with a language) and indented code,
  block quotes, bullet and numbered lists, thematic breaks, and HTML blocks.
- **Inlines:** `*emphasis*` and `**strong**` (with `_` too), code spans, links (`[text](url)` and by reference),
  images, entities and escapes, and hard line breaks after a `\`.
- **Scala 3's extensions:** tables, task lists, `~~strikethrough~~`, bare URLs and email addresses as links,
  footnotes (`[^1]`), and scaladoc's own `[[links]]` and `{{{ code }}}`.
- **Javadoc's inline tags**, as in wiki: `{@code x}`, `{@link x}` and the rest.

HTML is cleaned first, as Scala 3's scaladoc does it: the tags it keeps stay (`<b>`, `<code>`, `<span>`, `<table>` and
the like), `<p>` and `<div>` are paragraph breaks, and other tags go. As in Scala 3, that includes what only looks
like a tag, `<https://x.y>` or the `<A>` in `List<A>`:

```scala mdoc
parseMarkdown("/** A <b>safe</b> tag, a <font>gone</font> one, and List<A> or <https://x.y> */")
```

### How it was checked

The parser is checked against the examples of the GitHub Flavored Markdown spec, CommonMark's plus GitHub's: 650 of
the 672 parse to what the spec expects. The others differ on purpose, the way Scala 3's scaladoc does, which reads
Markdown with flexmark: as a check, the same examples run through flexmark with Scala 3's options, and the two parse
635 of them alike. The rest are corners where flexmark follows an earlier CommonMark. The parser stays linear on
pathological input, like ten thousand unclosed brackets, and nests at most 100 deep, as markdown-it does.

Two things Scala 3 does, the parser doesn't: `:emoji:` stays text, since the model has no node for it, and a table's
rows are as wide as its header, missing cells added and extra ones dropped.

## How this differs from scaladoc

Scaladoc renders comments to HTML, and this parser turns them into a model for other tools to use. A few choices
follow from that difference, and a few from fixing what scaladoc leaves to warnings:

- **Monospace is literal.** Scaladoc parses markup inside backticks; here the text of code stays as written.
- **Unclosed markers are text.** Scaladoc runs an unclosed `''` to the end of the paragraph. An unclosed `<b>` or
  `{@code`, though, is an error.
- **Bold inside italic works.** In `''a '''b''' c''`, scaladoc ends the italic at the `'''`; here the bold nests.
- **Broken tables aren't tables.** Without a valid delimiter row, scaladoc repairs the table and warns; here the lines
  stay text, which is what GitHub does and what scaladoc's own source says it should do.
- **HTML becomes the model.** Scaladoc passes `<b>` on as HTML; here it's `Bold`, and HTML in code blocks is left
  alone.
- **`{@literal}` is text**, as in javadoc. Scaladoc shows it as code.
- **`$super` stays as written.** In a class comment, Scala 3's scaladoc replaces it with the nearest parent's comment.
