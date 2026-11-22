# The model

> The map is not the territory.
>
> — Alfred Korzybski

A parsed comment is a map of the comment, and like every map it leaves things out on purpose. The border is gone,
and so are the exact spaces and how a list item was indented. What remains is what the comment *says*: which tags it
has, and how its text is structured. This page is the legend of that map.

```scala mdoc:invisible
import scaladoc._
import scaladoc.Markup._
```

## Scaladoc and its tags

A `Scaladoc` is a list of tags, in the order they appear. The description, the text before the first tag, is a tag
too. Most tags hold their text as `Markup`. A few, like `@author` or `@since`, hold plain strings, and some, like
`@documentable`, hold nothing. The @ref:[syntax page](syntax.md#tags) has the full list.

```
Tag      = Description       (Markup)
         | Constructor       (Markup)
         | Param             (String Markup)
         | TypeParam         (String Markup)
         | Returns           (Markup)
         | Throws            (String Markup)
         | See | Note | Example | UseCase | Todo | Deprecated | Migration   (Markup)
         | Author | Version | Since                                       (String)
         | Group             (String)
         | GroupName         (String String)
         | GroupDescription  (String Markup)
         | GroupPriority     (String Int)
         | Documentable
         | InheritDoc
         | OtherTag          (String Markup)

Scaladoc = List Tag
```

## Markup

Markup comes in two kinds: blocks, which stack on top of each other, and spans, which run inline within a block. A
`Document` holds blocks, and a tag's text is always a `Document`:

```
Span     = PlainText   (String)
         | Monospace   (String)
         | Italic      (List Span)
         | Bold        (List Span)
         | Underline   (List Span)
         | Superscript (List Span)
         | Subscript   (List Span)
         | Link        (String (List Span))        -- target, title
         | HtmlTag     (String)                    -- an HTML tag the model has no node for

Markup   = Span
         | Paragraph     (List Span)
         | Heading       (Level (List Span))       -- Level: One to Six
         | CodeBlock     (String)
         | UnorderedList (List Document)           -- each item: its text and nested lists
         | OrderedList   (List Document Style)     -- Style: Decimal, LowerRoman, UpperRoman, LowerAlpha, UpperAlpha
         | Table         (Row (List Alignment) (List Row))   -- a Row: one Paragraph a cell
         | HorizontalRule
         | Document      (List Markup)
```

The spans that hold other spans can nest without limit, so walking the model is naturally recursive. Here is a
function that collects every link target in a piece of markup, however deep it sits:

```scala mdoc
def links(m: Markup): List[String] = m match {
  case Link(target, title)     => target :: title.flatMap(links)
  case Document(elements)      => elements.flatMap(links)
  case Paragraph(spans)        => spans.flatMap(links)
  case Heading(_, spans)       => spans.flatMap(links)
  case x: HasSpans             => x.spans.flatMap(links)
  case x: ListBlock            => x.items.flatMap(links)
  case Table(header, _, rows)  => (header :: rows).flatMap(_.cells).flatMap(links)
  case _                       => Nil
}

Scaladoc.fromString(
  """/** See [[scala.Option]].
    |  *
    |  *   - '''also [[scala.Either]]'''
    |  */""".stripMargin).map(_.descriptions.flatMap(links))
```

Building markup by hand is just as direct. Every span has a shorthand for the common case of a single piece of text:

```scala mdoc
Paragraph(PlainText("Use "), Bold("this"), PlainText(" or "), Link("scala.Option"))
```

## Text, when markup is too much

> Omit needless words.
>
> — William Strunk Jr., *The Elements of Style*

Many tools want a string, not a tree: a schema needs a description, a log line needs a message. Two methods give one.
`plainString` writes the markup back in wiki syntax, and `trimmed` tidies the whitespace first, which is usually what
you want:

```scala mdoc
val m = Scaladoc.fromString("/** A   ''nice''\n  *   description. */").map(_.descriptions.head)

m.map(_.plainString)
m.map(_.trimmed.plainString)
```

`Scaladoc` has helpers for the parts that tools ask for most. Each comes in two flavors: one that returns the markup,
and a `text` one that returns it as trimmed plain text.

| Markup         | Text               | What                                                 |
|----------------|--------------------|------------------------------------------------------|
| `descriptions` | `textDescriptions` | the description                                      |
| `params`       | `textParams`       | `@param`s by name                                    |
| `typeParams`   | `textTypeParams`   | `@tparam`s by name                                   |
| `throws`       | `textThrows`       | `@throws` by exception type                          |
| `returns`      | `textReturns`      | `@return`                                            |
| `summary`      | `textSummary`      | the first sentence, as scaladoc shows it in member lists |

The summary is the description's first paragraph up to and with its first full stop, the one followed by a space or
the end. A `@shortDescription` tag replaces it:

```scala mdoc
Scaladoc.fromString(
  """/** Keeps the users of the app. All of them,
    |  * in one place.
    |  */""".stripMargin).map(_.textSummary)
```
