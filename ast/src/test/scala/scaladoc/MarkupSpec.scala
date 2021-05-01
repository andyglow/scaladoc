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
}
