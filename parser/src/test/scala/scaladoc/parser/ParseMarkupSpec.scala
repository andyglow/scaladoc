package scaladoc.parser

import org.scalactic.source.Position
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import scaladoc._
import scaladoc.Markup._
import scaladoc.Markup.Heading._
import scaladoc.matchers.Matchers._


class ParseMarkupSpec extends AnyWordSpec {

  "ParseMarkup" should {

    "handle empty docs" in {
      ParseMarkup("") mustBe Document(Nil)
      ParseMarkup("   ") mustBe Document(PlainText("   "))
    }

    "handle basic markup" when {
      def testMatching(str: String, expectation: Document)(implicit pos: Position): Unit = {
        withClue(s"[$str]") { ParseMarkup(str) mustBe expectation }
      }

      def testSuite(str: String, expectation: Markup): Unit = {
        testMatching(str, Document(expectation))
        testMatching(s" $str ", Document(PlainText(" "), expectation, PlainText(" ")))
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
          """
            | val p = Person("Al", 42, 200.0)
            | p.name
            | p.age
            | p.weight
            |""".stripMargin),
        Paragraph(
          PlainText("Did you know: The "),
          Link("com.acme.foo.Employee"),
          PlainText(" extends this class.")))))
    }
  }
}
