package scaladoc

import scaladoc.Markup._

// the nodes Markdown brings into the model; the parser produces them from #167's later steps on
class MarkdownNodesSpec extends FSpec {

  test("Strikethrough") {
    Strikethrough("gone").plainString mustBe "~~gone~~"
    Strikethrough(PlainText("a "), Bold("b")).plainString mustBe "~~a '''b'''~~"
    Strikethrough(" gone ").trimmed mustBe Strikethrough("gone")
    Strikethrough(" ").isBlank mustBe true
  }

  test("Image") {
    Image("logo.png", "the logo").plainString mustBe "![the logo](logo.png)"
    Image("logo.png") mustBe Image("logo.png", "")
    Image(" logo.png ", " the\n  logo ").trimmed mustBe Image("logo.png", "the\nlogo")
    Image("logo.png").isBlank mustBe false
    Image(" ", "").isBlank mustBe true
  }

  test("LineBreak") {
    Paragraph(PlainText("one"), LineBreak, PlainText("two")).plainString mustBe "one\ntwo"
    Paragraph(PlainText("one "), LineBreak, PlainText(" two")).trimmed.plainString mustBe "one \n two"
    Paragraph(LineBreak).isBlank mustBe true
    LineBreak.trimmed mustBe LineBreak
  }

  test("Checkbox") {
    Paragraph(Checkbox(checked = true), PlainText(" done")).plainString mustBe "[x] done"
    Paragraph(Checkbox(checked = false), PlainText(" to do ")).trimmed.plainString mustBe "[ ] to do"
    UnorderedList(List(Document(Paragraph(Checkbox(checked = true), PlainText(" done"))))).plainString mustBe "- [x] done"
    Checkbox(checked = false).isBlank mustBe false
  }

  test("CodeBlock's language") {
    CodeBlock("val x = 1") mustBe CodeBlock("val x = 1", None)
    CodeBlock("val x = 1", "scala") mustBe CodeBlock("val x = 1", Some("scala"))
    CodeBlock("  val x = 1  ", "scala").trimmed mustBe CodeBlock("val x = 1", "scala")
    CodeBlock("val x = 1", "scala").plainString mustBe "{{{val x = 1}}}"
  }

  test("BlockQuote") {
    val quote = BlockQuote(Paragraph(PlainText("quoted")), UnorderedList(List(Document(Paragraph(PlainText("item"))))))
    quote.plainString mustBe "> quoted\n> - item"
    BlockQuote(Paragraph(PlainText("a\n\nb"))).plainString mustBe "> a\n>\n> b"
    BlockQuote(Paragraph(PlainText("  quoted "))).trimmed mustBe BlockQuote(Paragraph(PlainText("quoted")))
    BlockQuote(Document(Nil)).isBlank mustBe true
    BlockQuote(BlockQuote(Paragraph(PlainText("deep")))).plainString mustBe "> > deep"
  }

  test("footnotes") {
    Paragraph(PlainText("See"), FootnoteReference("1"), PlainText(".")).plainString mustBe "See[^1]."
    FootnoteReference(" 1 ").trimmed mustBe FootnoteReference("1")
    FootnoteReference("1").isBlank mustBe false

    val note = Footnote("1", Document(Paragraph(PlainText("First.")), Paragraph(PlainText("Second."))))
    note.plainString mustBe "[^1]: First.\n    Second."
    Footnote(" 1 ", Document(Paragraph(PlainText(" text ")))).trimmed mustBe Footnote("1", Document(Paragraph(PlainText("text"))))
    Footnote("1", Document(Nil)).isBlank mustBe true
  }

  test("the text helpers see through the new nodes") {
    val doc = Scaladoc(
      Tag.Description(Document(Paragraph(PlainText("Some "), Strikethrough("old"), PlainText(" text.")))),
      Tag.Param("x", Document(BlockQuote(Paragraph(PlainText("quoted")))))
    )
    doc.textDescriptions mustBe Seq("Some ~~old~~ text.")
    doc.textParams mustBe Map("x" -> "> quoted")
  }
}
