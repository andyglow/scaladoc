package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._

// HTML in Markdown comments, as Scala 3's scaladoc cleans it before it parses Markdown (its `Cleaner`): the safe tags
// stay, `<p>` and `<div>` are paragraph breaks, headings `= ... =` and list items ` *  - `, and other tags go
class MarkdownHtmlCleaningSpec extends FSpec {

  private def description(comment: String): List[Markup] =
    Scaladoc.fromString(comment, syntax = CommentSyntax.Markdown).fold(err => fail(err.getMessage), identity)
      .tags.collect { case Description(Document(els)) => els }.flatten

  private def text(x: String) = Paragraph(PlainText(x))

  test("safe tags stay, others go") {
    description("/** a <b>bold</b> and <span class=\"x\">b</span>, but no <font color=\"red\">color</font> or <B>caps</B> */") mustBe List(
      Paragraph(
        PlainText("a "), HtmlTag("<b>"), PlainText("bold"), HtmlTag("</b>"), PlainText(" and "),
        HtmlTag("<span class=\"x\">"), PlainText("b"), HtmlTag("</span>"), PlainText(", but no color or caps")))
  }

  test("<p> and <div> are paragraph breaks") {
    description("/** One<p>Two</p><div class=\"note\">Three</div> */") mustBe List(text("One"), text("Two"), text("Three"))
  }

  test("an HTML block of tags that go is its text") {
    description("/** <div class=\"note\">\n  * Careful.\n  * </div>\n  *\n  * After.\n  */") mustBe List(text("Careful."), text("After."))
  }

  test("headings are `= ... =` text, as in Scala 3's Markdown") {
    description("/** <h2>Title</h2>Text */") mustBe List(text("== Title ==\nText"))
  }

  test("a `<` with a space after it is text") {
    description("/** if a < b then */") mustBe List(text("if a < b then"))
  }

  test("what Scala 3 takes for tags goes, as there: an autolink, `<A>`, HTML in javadoc's tags") {
    description("/** See <https://x.y> or List<A> here, and {@code Map<K, V>} too */") mustBe List(
      Paragraph(PlainText("See  or List here, and "), Monospace("Map"), PlainText(" too")))
  }

  test("fenced code and [[links]] stay as they are") {
    description("/** ```\n  * <div>x</div>\n  * ```\n  *\n  * [[a.<b>]]\n  */") mustBe List(
      CodeBlock("<div>x</div>"), Paragraph(Link("a.<b>")))
  }

  test("wiki's HTML is as before") {
    Scaladoc.fromString("/** One<p>Two</p> */").map(_.descriptions) mustBe Right(List(Document(text("One"), text("Two"))))
  }

  test("headings of every level, and list items, as Scala 3 cleans them") {
    // headings are `= ... =` text, and an item ` *  - `, which Markdown reads as a list in a list, as in Scala 3
    description("/** <h1>A</h1>x <h3>B</h3>y <h5>C</h5>z <ul><li>one</li></ul> */") mustBe List(
      text("= A =\nx\n=== B ===\ny\n==== C ====\nz"),
      UnorderedList(List(Document(UnorderedList(List(Document(text("one"))))))))
  }
}
