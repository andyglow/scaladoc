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
}
