# Scaladoc.of

> Any sufficiently advanced technology is indistinguishable from magic.
>
> — Arthur C. Clarke, *Profiles of the Future*

`Scaladoc.of[T]` looks like it shouldn't work. It's an ordinary expression, and yet it returns the doc comment written
above `T`'s definition, which is something the running program never had. The trick is that it doesn't run at runtime
at all. It's a macro: it runs inside the compiler, which does know the comment, parses it there, and leaves the
finished model behind in your code as a constant.

```scala mdoc
import scaladoc._

object geometry {

  /** A point on a plane.
    *
    * @param x the horizontal coordinate
    * @param y the vertical one
    */
  case class Point(x: Double, y: Double)
}

Scaladoc.of[geometry.Point].map(_.textParams)
```

That makes it a natural building block for derivation. A macro or a type class instance that describes `T` can call
`Scaladoc.of[T]` and get titles and descriptions for free. A type without a comment gives `None`.

What remains is the question of *where* the compiler finds the comment, and the answer is different on Scala 2 and on
Scala 3.

## On Scala 2

The Scala 2 compiler keeps a comment only while it compiles the source file the comment is in, and forgets it
afterwards. `Scaladoc.of` therefore checks three places, in this order:

1. **macro attachments**, where other macros may have left a comment
2. **the `@ScaladocCarrier` annotation** that the @ref:[compiler plugin](compiler-plugin.md) adds to the bytecode of a
   documented class. This is what makes classes from other modules and from jars work
3. **the source code**, when the class is compiled in the same run as the call

The third place is fragile. An incremental compile that doesn't recompile the class's file loses the comment. For
anything beyond a single module, the compiler plugin is the reliable way.

## On Scala 3

Scala 3 keeps doc comments in TASTy, the format it stores type information in, and `Scaladoc.of` reads them from
`Symbol.docstring`. By default, though, the compiler only loads the comments of the sources it compiles in the current
run. To read them from TASTy as well (other modules, jars, and files skipped by an incremental compile), compile the
module that calls `Scaladoc.of` with `-Xread-docs` (`-Yread-docs` before Scala 3.5, and deprecated since):

```sbt
scalacOptions += "-Xread-docs"
```

The @ref:[sbt](getting-started.md#the-sbt-plugin) and @ref:[Gradle](getting-started.md#gradle) plugins add it to every Scala 3
module they're enabled on, under the name its Scala version takes; @ref:[Maven](getting-started.md#maven) builds pass it in `scala-maven-plugin`'s `args`.

## Which syntax

`Scaladoc.of` reads a comment in the syntax of where it finds it:

| Found in | Written for | Read as |
|---|---|---|
| the source code, the annotation (Scala 2) | Scala 2 | wiki, Scala 2's only syntax |
| `Symbol.docstring` (Scala 3) | Scala 3 | Markdown, Scala 3's default |
| the annotation, on a Scala 2 class (Scala 3) | Scala 2 | wiki |

On Scala 3, a codebase that writes its comments in wiki syntax, as with scaladoc's `-comment-syntax wiki`, says so
with a given in scope where it calls `Scaladoc.of`, or each comment says it for itself, as in Scala 3's scaladoc:

```scala
import scaladoc.syntax.wiki.given   // Scaladoc.of reads wiki from here on

/** Some '''bold''' text.
  *
  * @syntax wiki
  */
```

`Scaladoc.of` reads the given's type, so it's `scaladoc.syntax.wiki` or `scaladoc.syntax.markdown`, which also works in
a library that calls `Scaladoc.of` from its own macros: the given in scope where those expand counts.

## Scala 2 libraries on Scala 3

A Scala 3 project can use Scala 2.13 libraries, and their classes have no TASTy to keep doc comments in. When a library
was compiled with the @ref:[compiler plugin](compiler-plugin.md), its classes carry their scaladoc in the
`@ScaladocCarrier` annotation, and `Scaladoc.of` reads it from there, as wiki, the syntax it was written in.

## A misaligned comment is a compile error

A comment whose `*` border wanders from line to line is usually a sign of a botched edit. `Scaladoc.of` checks the
alignment, on Scala 2 and on Scala 3 alike, and reports a comment it can't make sense of as a compile error at the call
site, with the comment and the reason. A comment without borders is fine; only a border that's there gets checked.

## From a string

When you have the comment as text, there's no need for a macro. `Scaladoc.fromString` parses it, in wiki syntax unless
told otherwise, with `syntax = CommentSyntax.Markdown`, or unless the comment says otherwise:

```scala mdoc
Scaladoc.fromString("/** Hello. */").map(_.textDescriptions)
```

Scala 3's `Symbol.docstring` returns a comment with one quirk: the indentation of its first line is gone, while the
other lines keep theirs. `Scaladoc.fromDocstring` parses that form. With `strict = true`, it puts the indentation
back and checks the border's alignment, exactly as `Scaladoc.of` does:

```scala mdoc
Scaladoc.fromDocstring("/** Nested class.\n    *\n    * @param a the a\n    */", strict = true).map(_.textParams)
```
