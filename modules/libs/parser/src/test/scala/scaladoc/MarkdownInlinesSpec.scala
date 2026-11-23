package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._

// Markdown's inline markup, through `Scaladoc.fromString`
class MarkdownInlinesSpec extends FSpec {

  private def spans(text: String): List[Span] =
    Scaladoc.fromString(s"/** $text */", syntax = CommentSyntax.Markdown).fold(err => fail(err.getMessage), identity).tags match {
      case List(Description(Document(List(Paragraph(xs))))) => xs
      case other                                             => fail(s"not one paragraph: $other")
    }

  test("emphasis: `*` and `_`, one for italic, two for bold") {
    spans("*a* _b_ **c** __d__ ***e***") mustBe List(
      Italic("a"), PlainText(" "), Italic("b"), PlainText(" "), Bold("c"), PlainText(" "), Bold("d"), PlainText(" "),
      Italic(Bold("e")))
  }

  test("`__` is bold in Markdown, not underline as in wiki") {
    spans("__x__") mustBe List(Bold("x"))
    Scaladoc.fromString("/** __x__ */").map(_.descriptions) mustBe Right(List(Document(Paragraph(Underline("x")))))
  }

  test("emphasis nests, and `_` inside words is text") {
    spans("**bold *and italic* text**") mustBe List(Bold(PlainText("bold "), Italic("and italic"), PlainText(" text")))
    spans("snake_case_name and 2*3*4") mustBe List(PlainText("snake_case_name and 2"), Italic("3"), PlainText("4"))
  }

  test("strikethrough is `~~`; one `~` is text") {
    spans("~~gone~~ and ~not~") mustBe List(Strikethrough("gone"), PlainText(" and ~not~"))
  }

  test("code spans") {
    spans("`a` and `` b ` c `` and `*not em*`") mustBe List(
      Monospace("a"), PlainText(" and "), Monospace("b ` c"), PlainText(" and "), Monospace("*not em*"))
  }

  test("links: inline, by reference, and autolinks") {
    Scaladoc.fromString(
      """/** See [the docs](https://x.y/docs "Docs"), [the spec][spec], [spec] and <https://x.y>.
        |  *
        |  * [spec]: https://x.y/spec
        |  */""".stripMargin,
      syntax = CommentSyntax.Markdown).map(_.descriptions) mustBe Right(List(Document(Paragraph(
      PlainText("See "), Link("https://x.y/docs", "the docs"), PlainText(", "), Link("https://x.y/spec", "the spec"),
      PlainText(", "), Link("https://x.y/spec", "spec"), PlainText(" and "), Link("https://x.y", "https://x.y"),
      PlainText(".")))))
  }

  test("bare URLs and email addresses are links, as GFM has them") {
    spans("Go to www.x.y, https://x.y/a?b=1 or ann@x.y.") mustBe List(
      PlainText("Go to "), Link("http://www.x.y", "www.x.y"), PlainText(", "), Link("https://x.y/a?b=1", "https://x.y/a?b=1"),
      PlainText(" or "), Link("mailto:ann@x.y", "ann@x.y"), PlainText("."))
  }

  test("scaladoc's [[links]], with an optional text") {
    spans("[[scala.Option]] or [[scala.util.Either the either]]") mustBe List(
      Link("scala.Option"), PlainText(" or "), Link("scala.util.Either", "the either"))
  }

  test("a [[link]] splits at its first space that isn't escaped, as Scala 3's does") {
    // Scala 3's own cases, from its DocFlexmarkParserTests
    spans("[[a b c d]]") mustBe List(Link("a", "b c d"))
    spans("[[a b\\ c d]]") mustBe List(Link("a", "b\\ c d"))
    spans("[[a\\ b c d]]") mustBe List(Link("a\\ b", "c d"))
    spans("[[a\\\\ b c d]]") mustBe List(Link("a\\\\", "b c d"))
  }

  test("images, with their text as the alternative") {
    spans("![the *logo*](logo.png)") mustBe List(Image("logo.png", "the logo"))
  }

  test("hard line breaks: a backslash at the end of a line") {
    // two spaces at the end of a line would be one too, but a comment's lines lose their trailing whitespace, as in
    // Scala 3's scaladoc
    Scaladoc.fromString("/** one  \n  * two\\\n  * three\n  * four */", syntax = CommentSyntax.Markdown).map(_.descriptions) mustBe
      Right(List(Document(Paragraph(PlainText("one\ntwo"), LineBreak, PlainText("three\nfour")))))
  }

  test("escapes and entities") {
    spans("""\*not em\* &copy; &#169; &#xA9; &bogus;""") mustBe List(PlainText("*not em* © © © &bogus;"))
  }

  test("javadoc's inline tags, as in wiki") {
    spans("{@code Map<K, V>}, {@link scala.Option the *option*} and {@literal <x>}") mustBe List(
      Monospace("Map<K, V>"), PlainText(", "), Link("scala.Option", List(PlainText("the "), Italic("option"))),
      PlainText(" and <x>"))
    spans("{@inheritDoc} and {@code open") mustBe List(PlainText("{@inheritDoc} and {@code open"))
  }

  test("inline HTML stays as it is") {
    spans("a <span class=\"x\">b</span>") mustBe List(PlainText("a "), HtmlTag("<span class=\"x\">"), PlainText("b"), HtmlTag("</span>"))
  }

  test("markup in task list items, table cells and headings") {
    Scaladoc.fromString(
      """/** # The `run` method
        |  *
        |  * - [x] **done**
        |  *
        |  * | a | b |
        |  * |---|---|
        |  * | *x* | `y` |
        |  */""".stripMargin,
      syntax = CommentSyntax.Markdown).map(_.descriptions) mustBe Right(List(Document(
      Heading(Heading.One, List(PlainText("The "), Monospace("run"), PlainText(" method"))),
      UnorderedList(List(Document(Paragraph(Checkbox(checked = true), PlainText(" "), Bold("done"))))),
      Table(
        Table.Row(List(Paragraph(PlainText("a")), Paragraph(PlainText("b")))),
        List(Table.Alignment.Left, Table.Alignment.Left),
        List(Table.Row(List(Paragraph(Italic("x")), Paragraph(Monospace("y")))))
      )
    )))
  }

  test("tags' bodies have inline markup too") {
    Scaladoc.fromString("/** Adds.\n  *\n  * @param x the **first** one\n  */", syntax = CommentSyntax.Markdown)
      .map(_.params) mustBe Right(Map("x" -> Document(Paragraph(PlainText("the "), Bold("first"), PlainText(" one")))))
  }
}
