# Scaladoc

> Programs must be written for people to read, and only incidentally for machines to execute.
>
> — Harold Abelson and Gerald Jay Sussman, *Structure and Interpretation of Computer Programs*

Every Scala codebase carries a second program, written in prose. It sits in the doc comments above classes and
methods, and it explains what the code means: what a field holds, what a parameter is for, why an exception gets
thrown. The compiler reads it once, mostly to throw it away, and `scaladoc` turns it into a web site. Then it rests.

That's a waste, because the prose is often exactly what other tools are missing. A JSON Schema derived from a case
class can say that a field is a string, but not that it's *the name shown to other users*. An OpenAPI spec knows the
shape of a request, not its purpose. The purpose is right there in the source, one line above the shape.

This library puts that prose to work. It has five parts:

- **a model** of what a doc comment says: its tags (`@param`, `@return`, ...) and its markup (paragraphs, headings,
  lists, tables, code, links, bold and the rest)
- **a parser** that turns comment text into the model. It reads scaladoc's wiki markup and Markdown, as Scala 3's
  scaladoc does, with HTML and javadoc's inline tags, on the JVM and on Scala.js
- **`Scaladoc.of[T]`**, which hands you the scaladoc of a type at compile time, in macros and type class derivation
- **`Typedoc.of[T]`**, which hands you the scaladoc of a sealed hierarchy or an enum with its variants', and of a case
  class with its fields', as a tree
- **a compiler plugin** (Scala 2) that embeds the scaladoc of classes into their bytecode, so it survives the trip
  into a jar

Here is the whole idea in a few lines. A case class and a sealed hierarchy document themselves, and the code asks them
what they said:

```scala mdoc:silent
import scaladoc._

object model {

  /** A user of the app.
    *
    * Users sign up with an e-mail address and can change their name later.
    *
    * @param name the name shown to other users
    * @param age  how old they are, in years
    */
  case class User(name: String, age: Int)

  /** What a user may do. */
  sealed trait Role

  /** Can do anything, invite others included. */
  case object Admin extends Role

  /** Can read and comment.
    *
    * @param team the team they're in
    */
  case class Member(team: String) extends Role

  /** Can look, for a while. */
  sealed trait Guest extends Role

  /** Invited by a member.
    *
    * @param by who invited them
    */
  case class Invited(by: String) extends Guest

  /** Came in by a shared link. */
  final class Anonymous extends Guest
}

import model._

val doc = Scaladoc.of[User].get
```

`Scaladoc.of` gives a type's comment:

```scala mdoc
doc.textSummary
doc.textParams
```

`textSummary` is the first sentence, the one scaladoc itself would show in a list of members. `textParams` is what
each field means. A schema generator needs nothing more to give every property a title and a description.

`Typedoc.of` gives the comments of a type made of others, as a tree. Here each `Some(doc)` is shown as its text:

```scala mdoc:compile-only
Typedoc.of[Role]
// Sum("Role", "What a user may do.", List(
//   Singleton("Admin", "Can do anything, invite others included."),
//   Product("Member", "Can read and comment.", List(Field("team", "the team they're in"))),
//   Sum("Guest", "Can look, for a while.", List(
//     Product("Invited", "Invited by a member.", List(Field("by", "who invited them"))),
//     Plain("Anonymous", "Came in by a shared link.")))))
```

```scala mdoc:invisible
// what the comment above says, checked
def shown(t: Typedoc, indent: String): String = {
  def text(doc: Option[Scaladoc]) = "\"" + doc.flatMap(_.textSummary).get + "\""
  t match {
    case Typedoc.Sum(n, d, vs) =>
      s"Sum(\"$n\", ${text(d)}, List(\n" + vs.map(v => indent + "  " + shown(v, indent + "  ")).mkString(",\n") + "))"
    case Typedoc.Product(n, d, fs) =>
      s"Product(\"$n\", ${text(d)}, List(" +
        fs.map(f => s"Field(\"${f.name}\", \"${f.doc.get.trimmed.plainString}\")").mkString(", ") + "))"
    case Typedoc.Singleton(n, d) => s"Singleton(\"$n\", ${text(d)})"
    case Typedoc.Plain(n, d)     => s"Plain(\"$n\", ${text(d)})"
  }
}
assert(shown(Typedoc.of[Role], "") ==
  """Sum("Role", "What a user may do.", List(
    |  Singleton("Admin", "Can do anything, invite others included."),
    |  Product("Member", "Can read and comment.", List(Field("team", "the team they're in"))),
    |  Sum("Guest", "Can look, for a while.", List(
    |    Product("Invited", "Invited by a member.", List(Field("by", "who invited them"))),
    |    Plain("Anonymous", "Came in by a shared link.")))))""".stripMargin, shown(Typedoc.of[Role], ""))
```

- `Sum`: a sealed trait or class, with its variants, which can be sums themselves
- `Product`: a case class, with its fields, each documented by its `@param`
- `Singleton`: an object
- `Plain`: any other type

A Scala 3 enum is a `Sum` of its cases, a case with parameters a `Product`, and one without a `Singleton`:

```scala
/** How a user hears from the app. */
enum Channel {

  /** Not at all. */
  case Silent

  /** By e-mail.
    *
    * @param perDay how many a day, at most
    */
  case Email(perDay: Int)
}

Typedoc.of[Channel]
// Sum("Channel", "How a user hears from the app.", List(
//   Singleton("Silent", "Not at all."),
//   Product("Email", "By e-mail.", List(Field("perDay", "how many a day, at most")))))
```

A schema generator makes a sealed trait a `oneOf`, with a description for the whole and one for each alternative.

## Supported versions

| Module                     | Scala 2.12 | Scala 2.13 | Scala 3 (3.9+) | Scala.js 1.x |
|----------------------------|:----------:|:----------:|:--------------:|:------------:|
| `scaladoc-core`             |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-parser`          |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-compiler-plugin` |     ✓      |     ✓      |       –        |  JVM one     |
| `scaladoc-annotation`      |     ✓      |     ✓      |       ✓        |      ✓       |

`scaladoc-annotation` is a single Java jar, with no Scala suffix, for all of them. `sbt-scaladoc` works with sbt 1.5.8+
and sbt 2.x, and the Gradle plugin with Gradle 8.5+. Scala 2 artifacts
target Java 8, Scala 3 ones Java 17 (Scala 3.9 needs it), and run on any later JDK.

## Where to go next

If you want to try it, start with @ref:[Getting started](getting-started.md). If you write the comments,
@ref:[Comment syntax](syntax.md) shows everything the parser understands. If you write the tools that read them,
@ref:[The model](model.md) and @ref:[Scaladoc.of and Typedoc.of](extractor.md) are for you.

@@@ index

* [Getting started](getting-started.md)
* [Comment syntax](syntax.md)
* [The model](model.md)
* [Scaladoc.of and Typedoc.of](extractor.md)
* [Compiler plugin](compiler-plugin.md)
* [Migrating](migration.md)

@@@
