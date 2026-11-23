package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._

// Markdown's blocks, through `Scaladoc.fromString`: the comment's border is stripped and its tags split off first.
class MarkdownBlocksSpec extends FSpec {

  private def parse(comment: String): Scaladoc =
    Scaladoc.fromString(comment, syntax = CommentSyntax.Markdown).fold(err => fail(err.getMessage), identity)

  private def description(comment: String): List[Markup] = parse(comment).tags.collect { case Description(Document(els)) => els }.flatten

  private def text(x: String) = Paragraph(PlainText(x))

  test("headings, paragraphs and a rule") {
    description(
      """/** # Users
        |  *
        |  * Who they are,
        |  * and what they do.
        |  *
        |  * Roles
        |  * -----
        |  *
        |  * ***
        |  */""".stripMargin) mustBe List(
      Heading(Heading.One, "Users"),
      text("Who they are,\nand what they do."),
      Heading(Heading.Two, "Roles"),
      HorizontalRule
    )
  }

  test("fenced code, with its language, and an `@` in it that isn't a tag") {
    val doc = parse(
      """/** Runs it:
        |  *
        |  * ```scala
        |  * @main def run(): Unit =
        |  *   println("hi")
        |  * ```
        |  *
        |  * @param x the x
        |  */""".stripMargin)
    doc mustBe Scaladoc(
      Description(Document(text("Runs it:"), CodeBlock("@main def run(): Unit =\n  println(\"hi\")", "scala"))),
      Param("x", Document(text("the x")))
    )
  }

  test("a fence without a closing one doesn't hide the tags after it") {
    parse(
      """/** ```
        |  * val x = 1
        |  *
        |  * @param x the x
        |  */""".stripMargin) mustBe Scaladoc(
      // the description is all code, as it runs to the end of the description
      Description(Document(CodeBlock("val x = 1\n"))),
      Param("x", Document(text("the x")))
    )
  }

  test("tildes, indented code, and scaladoc's {{{ }}}") {
    description(
      """/** ~~~
        |  * a
        |  * ~~~
        |  *
        |  *     b
        |  *
        |  * {{{
        |  *   c
        |  * }}}
        |  */""".stripMargin) mustBe List(CodeBlock("a"), CodeBlock("b"), CodeBlock("c"))
  }

  test("lists: bullets, numbers, nesting and tasks") {
    description(
      """/** - one
        |  * - two
        |  *   1. a
        |  *   2. b
        |  *
        |  * + [x] done
        |  * + [ ] to do
        |  */""".stripMargin) mustBe List(
      UnorderedList(
        List(
          Document(text("one")),
          Document(text("two"), OrderedList(List(Document(text("a")), Document(text("b"))), OrderedList.Decimal))
        )),
      // a different bullet is a new list
      UnorderedList(
        List(
          Document(Paragraph(Checkbox(checked = true), PlainText(" done"))),
          Document(Paragraph(Checkbox(checked = false), PlainText(" to do")))
        ))
    )
  }

  test("block quotes, nested, with their content and lazy lines") {
    description(
      """/** > Quoted,
        |  * and continued.
        |  * >
        |  * > > deeper
        |  * > - item
        |  */""".stripMargin) mustBe List(
      BlockQuote(
        text("Quoted,\nand continued."),
        BlockQuote(text("deeper")),
        UnorderedList(List(Document(text("item"))))
      )
    )
  }

  test("tables, with alignments, an escaped pipe, and rows of any width") {
    description(
      """/** | name | `a\|b` | age |
        |  * |:-----|:------:|----:|
        |  * | ann  | x      | 31  |
        |  * | bob  |
        |  */""".stripMargin) mustBe List(
      Table(
        // as in Scala 3, a `\|` in a code span stays one
        Table.Row(List(text("name"), Paragraph(Monospace("a\\|b")), text("age"))),
        List(Table.Alignment.Left, Table.Alignment.Center, Table.Alignment.Right),
        List(
          Table.Row(List(text("ann"), text("x"), text("31"))),
          Table.Row(List(text("bob"), Paragraph(Nil), Paragraph(Nil)))
        )
      )
    )
  }

  test("an HTML block of safe tags stays as it is") {
    description(
      """/** <table>
        |  * <tr><td>Careful.</td></tr>
        |  * </table>
        |  *
        |  * After.
        |  */""".stripMargin) mustBe List(HtmlTag("<table>\n<tr><td>Careful.</td></tr>\n</table>"), text("After."))
  }

  test("YAML front matter at the start goes, as in Scala 3") {
    description("/** ---\n  * title: x\n  * ---\n  * Text.\n  */") mustBe List(text("Text."))
  }

  test("tags' bodies are Markdown too") {
    parse(
      """/** Description.
        |  *
        |  * @param x one of
        |  *   - `a`
        |  *   - `b`
        |  * @return what it
        |  *   returns
        |  */""".stripMargin) mustBe Scaladoc(
      Description(Document(text("Description."))),
      Param("x", Document(text("one of"), UnorderedList(List(Document(Paragraph(Monospace("a"))), Document(Paragraph(Monospace("b"))))))),
      Returns(Document(text("what it\nreturns")))
    )
  }

  test("wiki stays the default") {
    val comment = "/** = Title =\n  *\n  * # not a heading\n  */"
    Scaladoc.fromString(comment).map(_.descriptions) mustBe Right(
      List(Document(Heading(Heading.One, "Title"), text("# not a heading"))))
    description(comment) mustBe List(text("= Title ="), Heading(Heading.One, "not a heading"))
  }
}
