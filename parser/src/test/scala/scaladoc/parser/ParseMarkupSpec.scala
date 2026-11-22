package scaladoc.parser

import org.scalactic.source.Position
import scaladoc._
import scaladoc.Markup._
import scaladoc.Markup.Heading._
import scaladoc.matchers.Matchers._


class ParseMarkupSpec extends WSpec {

  "ParseMarkup" must {

    "handle empty docs" in {
      ParseMarkup("") mustBe Document(Nil)
      ParseMarkup("   ") mustBe Document(Nil)
      ParseMarkup("\n  \n") mustBe Document(Nil)
    }

    "put text into paragraphs, also a single one and the one next to a block" in {
      ParseMarkup("one\nparagraph") mustBe Document(Paragraph(PlainText("one\nparagraph")))
      ParseMarkup("Before\n{{{x}}}\nAfter") mustBe Document(
        Paragraph(PlainText("Before")), CodeBlock("x"), Paragraph(PlainText("After")))
    }

    "handle basic markup" when {
      def testMatching(str: String, expectation: Document)(implicit pos: Position): Unit = {
        withClue(s"[$str]") { ParseMarkup(str) mustBe expectation }
      }

      def testSuite(str: String, expectation: Span): Unit = {
        testMatching(str, Document(Paragraph(expectation)))
        testMatching(s" $str ", Document(Paragraph(expectation)))
        testMatching(s"a $str b", Document(Paragraph(PlainText("a "), expectation, PlainText(" b"))))
      }

      def headingTestSuite(str: String, expectation: Heading): Unit = {
        testMatching(str, Document(expectation))
        testMatching(s" $str ", Document(expectation))
        testMatching(s"  $str ", Document(expectation))
        testMatching(s"\t $str ", Document(expectation))
        testMatching(s" $str\t", Document(expectation))
      }

      "monospace" in testSuite("`abc`", Monospace("abc"))
      "italic" in testSuite("''abc''", Italic("abc"))
      "bold" in testSuite("'''abc'''", Bold("abc"))
      "underline" in testSuite("__abc__", Underline("abc"))
      "superscript" in testSuite("^abc^", Superscript("abc"))
      "subscript" in testSuite(",,abc,,", Subscript("abc"))
      "link" in testSuite("[[abc]]", Link("abc"))

      "h1" in headingTestSuite("=abc=", Heading(One, "abc"))
      "h2" in headingTestSuite("==abc==", Heading(Two, "abc"))
      "h3" in headingTestSuite("===abc===", Heading(Three, "abc"))
      "h4" in headingTestSuite("====abc====", Heading(Four, "abc"))
      "h5" in headingTestSuite("=====abc=====", Heading(Five, "abc"))
      "h6" in headingTestSuite("======abc======", Heading(Six, "abc"))
      "h7 and deeper are h6" in headingTestSuite("=======abc=======", Heading(Six, "abc"))
    }

    "handle paragraphs" in {
      val doc = ParseMarkup(
        """Start the comment here
          |and use the left star followed by a
          |white space on every line.
          |
          |Even on empty paragraph-break lines.
          |
          |Note that the * on each line is aligned
          |with the second * in /** so that the
          |left margin is on the same column on the
          |first line and on subsequent ones.
          |
          |The closing Scaladoc tag goes on its own,
          |separate line. E.g.
          |
          |Calculate the square of the given number""".stripMargin)

      doc mustBe Document(
        Paragraph(PlainText(
          """Start the comment here
            |and use the left star followed by a
            |white space on every line.""".stripMargin)),
        Paragraph(PlainText("Even on empty paragraph-break lines.")),
        Paragraph(PlainText(
          """Note that the * on each line is aligned
            |with the second * in /** so that the
            |left margin is on the same column on the
            |first line and on subsequent ones.""".stripMargin)),
        Paragraph(PlainText(
          """The closing Scaladoc tag goes on its own,
            |separate line. E.g.""".stripMargin)),
        Paragraph(PlainText("Calculate the square of the given number")))
    }

    "handle little more complex example" in {
      val doc = ParseMarkup(
        """= A Title =
          |
          |A class to represent a ''human being''.
          |
          |== A Subtitle ==
          |Specify the `name`, `age`, and `weight` when creating a new `Person`,
          |then access the fields like this:
          |
          |{{{
          | val p = Person("Al", 42, 200.0)
          | p.name
          | p.age
          | p.weight
          |}}}
          |
          |Did you know: The [[com.acme.foo.Employee]] extends this class.""".stripMargin)

      doc must (coincideWith (Document(
        Heading(One, " A Title "),
        Paragraph(
          PlainText("A class to represent a "),
          Italic("human being"),
          PlainText(".")),
        Heading(Two, " A Subtitle "),
        Paragraph(
          PlainText("Specify the "),
          Monospace("name"),
          PlainText(", "),
          Monospace("age"),
          PlainText(", and "),
          Monospace("weight"),
          PlainText(" when creating a new "),
          Monospace("Person"),
          PlainText(",\nthen access the fields like this:")),
        CodeBlock(
          """val p = Person("Al", 42, 200.0)
            |p.name
            |p.age
            |p.weight""".stripMargin),
        Paragraph(
          PlainText("Did you know: The "),
          Link("com.acme.foo.Employee"),
          PlainText(" extends this class.")))))
    }

    "keep an unclosed marker as text" in {
      def text(x: String) = Document(Paragraph(PlainText(x)))
      ParseMarkup("x^2 is square") mustBe text("x^2 is square")
      ParseMarkup("a `b c") mustBe text("a `b c")
      ParseMarkup("it''s fine") mustBe text("it''s fine")
      ParseMarkup("'''a'' b") mustBe text("'''a'' b")
      ParseMarkup("[[open link") mustBe text("[[open link")
      ParseMarkup("{{{ never closed") mustBe text("{{{ never closed")
      ParseMarkup("x^2 and ^sup^") mustBe Document(Paragraph(PlainText("x"), Superscript("2 and "), PlainText("sup^")))
    }

    "not close a marker in the next paragraph" in {
      ParseMarkup("a `b\n\nc` d") mustBe Document(Paragraph(PlainText("a `b")), Paragraph(PlainText("c` d")))
      ParseMarkup("a ''b\nc'' d") mustBe Document(Paragraph(PlainText("a "), Italic("b\nc"), PlainText(" d")))
    }

    "let a code block span paragraphs" in {
      ParseMarkup("{{{\na\n\nb\n}}}") mustBe Document(CodeBlock("a\n\nb"))
    }

    "drop the indentation that all lines of a code block share" in {
      ParseMarkup("{{{\n\n    def f =\n      1\n\n    f   \n  }}}") mustBe Document(CodeBlock("def f =\n  1\n\nf"))
      ParseMarkup("{{{ x }}}") mustBe Document(CodeBlock("x"))
    }
  }
}
