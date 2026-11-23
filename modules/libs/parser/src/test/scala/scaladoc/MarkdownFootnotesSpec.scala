package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._

// Markdown's footnotes, as flexmark (Scala 3's) has them: `[^label]` refers to `[^label]: text`
class MarkdownFootnotesSpec extends FSpec {

  private def parse(comment: String): Scaladoc =
    Scaladoc.fromString(comment, syntax = CommentSyntax.Markdown).fold(err => fail(err.getMessage), identity)

  private def description(comment: String): List[Markup] =
    parse(comment).tags.collect { case Description(Document(els)) => els }.flatten

  private def text(x: String) = Paragraph(PlainText(x))

  test("a reference, and the footnote where it is in the comment") {
    description(
      """/** Some text[^1], and more.
        |  *
        |  * [^1]: The first footnote.
        |  */""".stripMargin) mustBe List(
      Paragraph(PlainText("Some text"), FootnoteReference("1"), PlainText(", and more.")),
      Footnote("1", Document(text("The first footnote.")))
    )
  }

  test("a footnote's text goes on in lines indented by 4, lazily, and in more paragraphs") {
    description(
      """/** See[^note].
        |  *
        |  * [^note]: One that goes on
        |  * lazily,
        |  *
        |  *     and in a *second* paragraph.
        |  *
        |  * After.
        |  */""".stripMargin) mustBe List(
      Paragraph(PlainText("See"), FootnoteReference("note"), PlainText(".")),
      Footnote("note", Document(text("One that goes on\nlazily,"), Paragraph(PlainText("and in a "), Italic("second"), PlainText(" paragraph.")))),
      text("After.")
    )
  }

  test("footnotes one after the other, and references before and after them") {
    description(
      """/** [^a]: First.
        |  * [^b]: Second.
        |  *
        |  * Both[^a][^b], case folded[^A].
        |  */""".stripMargin) mustBe List(
      Footnote("a", Document(text("First."))),
      Footnote("b", Document(text("Second."))),
      Paragraph(PlainText("Both"), FootnoteReference("a"), FootnoteReference("b"), PlainText(", case folded"), FootnoteReference("A"), PlainText("."))
    )
  }

  test("a reference to a footnote that isn't there is text") {
    description("/** Nothing[^x] here. */") mustBe List(text("Nothing[^x] here."))
  }

  test("in a tag's body") {
    parse(
      """/** Description.
        |  *
        |  * @param x the x[^why]
        |  *
        |  *   [^why]: Because.
        |  */""".stripMargin).params mustBe Map(
      "x" -> Document(Paragraph(PlainText("the x"), FootnoteReference("why")), Footnote("why", Document(text("Because.")))))
  }

  test("the text helpers") {
    val doc = parse("/** A[^1].\n  *\n  * [^1]: Note.\n  */")
    doc.textDescriptions mustBe Seq("A[^1].\n[^1]: Note.")
  }

  test("wiki has no footnotes") {
    Scaladoc.fromString("/** A[^1].\n  *\n  * [^1]: Note.\n  */").map(_.descriptions) mustBe Right(
      List(Document(text("A[^1]."), text("[^1]: Note."))))
  }
}
