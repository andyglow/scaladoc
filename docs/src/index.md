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

This library puts that prose to work. It has four parts:

- **a model** of what a doc comment says: its tags (`@param`, `@return`, ...) and its markup (paragraphs, headings,
  lists, tables, code, links, bold and the rest)
- **a parser** that turns comment text into the model. It reads scaladoc's wiki markup, HTML and javadoc's inline
  tags, on the JVM and on Scala.js
- **`Scaladoc.of[T]`**, which hands you the scaladoc of a type at compile time, in macros and type class derivation
- **a compiler plugin** (Scala 2) that embeds the scaladoc of classes into their bytecode, so it survives the trip
  into a jar

Here is the whole idea in a few lines. A case class documents itself, and the code asks it what it said:

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
}

import model._

val doc = Scaladoc.of[User].get
```

```scala mdoc
doc.textSummary
doc.textParams
```

`textSummary` is the first sentence, the one scaladoc itself would show in a list of members. `textParams` is what
each field means. A schema generator needs nothing more to give every property a title and a description.

## Supported versions

| Module                     | Scala 2.12 | Scala 2.13 | Scala 3 (3.3+) | Scala.js 1.x |
|----------------------------|:----------:|:----------:|:--------------:|:------------:|
| `scaladoc-ast`             |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-parser`          |     ✓      |     ✓      |       ✓        |      ✓       |
| `scaladoc-compiler-plugin` |     ✓      |     ✓      |       –        |  JVM one     |

`sbt-scaladoc` works with sbt 1.5.8+ and sbt 2.x. All artifacts target Java 8 and run on any later JDK.

## Where to go next

If you want to try it, start with @ref:[Getting started](getting-started.md). If you write the comments,
@ref:[Comment syntax](syntax.md) shows everything the parser understands. If you write the tools that read them,
@ref:[The model](model.md) and @ref:[Scaladoc.of](extractor.md) are for you.

@@@ index

* [Getting started](getting-started.md)
* [Comment syntax](syntax.md)
* [The model](model.md)
* [Scaladoc.of](extractor.md)
* [Compiler plugin](compiler-plugin.md)
* [Migrating to 0.4](migration.md)

@@@
