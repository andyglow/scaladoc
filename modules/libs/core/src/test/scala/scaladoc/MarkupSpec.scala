package scaladoc

class MarkupSpec extends FSpec {

  test("one line") {
    Markup.trimML("foo") mustBe "foo"
    Markup.trimML(" foo") mustBe "foo"
    Markup.trimML("foo ") mustBe "foo"
    Markup.trimML(" foo ") mustBe "foo"
    Markup.trimML("\t foo") mustBe "foo"
    Markup.trimML("foo\t ") mustBe "foo"
    Markup.trimML("\tfoo\t ") mustBe "foo"
  }

  test("multi line") {
    Markup.trimML("foo\n") mustBe "foo"
    Markup.trimML("\nfoo") mustBe "foo"
    Markup.trimML("\nfoo\n") mustBe "foo"
    Markup.trimML("\n\nfoo") mustBe "foo"
    Markup.trimML("\t \nfoo") mustBe "foo"
    Markup.trimML("foo\t\n\t\r\n") mustBe "foo"
  }

  test("Document.plainString") {
    import Markup._

    Document(Monospace("B"), PlainText(" Param")).plainString mustBe "`B` Param"
    Document(Monospace("B"), PlainText(" Param")).trimmed.plainString mustBe "`B` Param"
  }

  test("Paragraph.plainString") {
    import Markup._

    Paragraph(Monospace("B"), PlainText(" Param")).plainString mustBe "`B` Param"
    Paragraph(Monospace("B"), PlainText(" Param")).trimmed.plainString mustBe "`B` Param"
  }

  test("trimmed keeps the spacing between spans") {
    import Markup._

    Paragraph(PlainText(" an "), Monospace("a"), PlainText(" value ")).trimmed.plainString mustBe "an `a` value"
    Paragraph(PlainText("a"), Monospace("b"), PlainText("c")).trimmed.plainString mustBe "a`b`c"
    Paragraph(Bold("a"), PlainText("  \n "), Italic("b")).trimmed.plainString mustBe "'''a''' ''b''"
    Paragraph(PlainText("one \n  two "), Bold("x")).trimmed.plainString mustBe "one\ntwo '''x'''"
    Document(PlainText("an "), Monospace("a"), PlainText(" value")).trimmed.plainString mustBe "an `a` value"
    Document(Paragraph(PlainText(" a ")), PlainText("b"), CodeBlock("c")).trimmed.plainString mustBe "a\nb\n{{{c}}}"
  }

  test("superscript and subscript") {
    import Markup._

    Superscript("2").plainString mustBe "^2^"
    Subscript(" 2 ").trimmed mustBe Subscript("2")
    Subscript(" 2 ").trimmed.plainString mustBe ",,2,,"
  }

  test("Description.markup") {
    val m = Markup.PlainText("x")
    Tag.Description(m).markup mustBe m
  }
  test("plainString and trimmed of every span") {
    import Markup._

    val spans: List[(Span, String)] = List(
      PlainText(" a ")                     -> " a ",
      Monospace("a")                       -> "`a`",
      Italic(PlainText(" a "), Bold("b"))  -> "'' a '''b'''''",
      Bold(" a ")                          -> "''' a '''",
      Underline(" a ")                     -> "__ a __",
      Superscript(" a ")                   -> "^ a ^",
      Subscript(" a ")                     -> ",, a ,,",
      Link("scala.Option")                 -> "[[scala.Option]]")
    spans foreach { case (span, plain) => span.plainString mustBe plain }

    PlainText(" a ").trimmed mustBe PlainText("a")
    Italic(PlainText(" a "), Bold("b")).trimmed mustBe Italic(PlainText("a "), Bold("b"))
    Underline(" a ").trimmed mustBe Underline("a")
    Superscript(" a ").trimmed mustBe Superscript("a")
    Link(" x ").trimmed mustBe Link("x")
    Paragraph(Bold("a"), PlainText("   "), Italic("b")).trimmed mustBe Paragraph(Bold("a"), PlainText(" "), Italic("b"))
    Italic(PlainText(" ")).isBlank mustBe true
    Bold(PlainText(" "), Monospace("x")).isBlank mustBe false
  }

  test("headings") {
    import Markup._
    import Markup.Heading._

    (1 to 6).map(levelOf).map(_.value) mustBe (1 to 6)
    an[IllegalArgumentException] mustBe thrownBy(levelOf(7))
    Heading(3, "a") mustBe Heading(Three, List(PlainText("a")))
    Heading(Two, List(PlainText(" a "), Monospace("b"))).plainString mustBe " a `b`"
    Heading(Two, List(PlainText(" a "), Monospace("b"))).trimmed mustBe Heading(Two, List(PlainText("a "), Monospace("b")))
    Heading(One, " ").isBlank mustBe true
  }
  test("links") {
    import Markup._

    Link("scala.Option").plainString mustBe "[[scala.Option]]"
    Link("https://x.y", List(PlainText("the "), Italic("x"))).plainString mustBe "[[https://x.y the ''x'']]"
    Link(" https://x.y ", List(PlainText(" the x "))).trimmed mustBe Link("https://x.y", "the x")
    Link(" ", List(PlainText(" "))).isBlank mustBe true
    Link("x").isBlank mustBe false
    Paragraph(PlainText("  ")).trimmed mustBe Paragraph(Nil)
  }
  test("lists") {
    import Markup._

    def item(text: String, nested: Markup*) = Document(Paragraph(PlainText(text)) :: nested.toList)
    val list = UnorderedList(List(item(" a "), item("b", OrderedList(List(item("c"), item("d")), OrderedList.LowerRoman))))

    list.plainString mustBe "-  a \n- b\n  i. c\n  i. d"
    list.trimmed mustBe UnorderedList(List(item("a"), item("b", OrderedList(List(item("c"), item("d")), OrderedList.LowerRoman))))
    OrderedList(List(item("a")), OrderedList.UpperAlpha).trimmed.plainString mustBe "A. a"
    OrderedList(List(item("a")), OrderedList.Decimal).plainString mustBe "1. a"
    UnorderedList(List(Document(Nil))).plainString mustBe "- "
    UnorderedList(List(item(" "))).isBlank mustBe true
    OrderedList.styles.map(_.marker) mustBe List("1", "i", "I", "a", "A")
  }
  test("tables") {
    import Markup._
    import Markup.Table.Alignment._

    def row(cells: String*) = Table.Row(cells.toList.map(c => Paragraph(PlainText(c))))
    val table = Table(row(" a ", "b", "c"), List(Left, Center, Right), List(row("1", " 2 ", "3")))

    table.plainString mustBe "|  a  | b | c |\n| --- | :-: | --: |\n| 1 |  2  | 3 |"
    table.trimmed mustBe Table(row("a", "b", "c"), List(Left, Center, Right), List(row("1", "2", "3")))
    table.isBlank mustBe false
    Table(row(" "), List(Left), List(row(""))).isBlank mustBe true
  }

  test("horizontal rule") {
    Markup.HorizontalRule.plainString mustBe "----"
    Markup.HorizontalRule.isBlank mustBe false
    Markup.HorizontalRule.trimmed mustBe Markup.HorizontalRule
    Markup.Document(Markup.Paragraph(Markup.PlainText(" a ")), Markup.HorizontalRule).trimmed.plainString mustBe "a\n----"
  }

  test("HTML tags") {
    Markup.HtmlTag("<span>").plainString mustBe "<span>"
    Markup.HtmlTag("<span>").isBlank mustBe false
    Markup.HtmlTag("<span>").trimmed mustBe Markup.HtmlTag("<span>")
  }
}
