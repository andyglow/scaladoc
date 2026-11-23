package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._

class CommentSyntaxSpec extends FSpec {

  private def right(x: Either[ScaladocException, Scaladoc]): Scaladoc = x.fold(err => fail(err.getMessage), identity)

  // wiki markup, a tag, and code with an `@` at the start of a line: inside code that's code, not a tag
  private val comment =
    """/** Some '''bold''' and __underlined__ text.
      |  *
      |  * {{{
      |  * @main def run(): Unit = ()
      |  * }}}
      |  *
      |  * <pre>
      |  * @tailrec def loop(): Unit = loop()
      |  * </PRE>
      |  *
      |  * @param x the ''x''
      |  */""".stripMargin

  private val expected = Scaladoc(
    Description(
      Document(
        Paragraph(PlainText("Some "), Bold(List(PlainText("bold"))), PlainText(" and "), Underline(List(PlainText("underlined"))), PlainText(" text.")),
        CodeBlock("@main def run(): Unit = ()"),
        CodeBlock("@tailrec def loop(): Unit = loop()")
      )
    ),
    Param("x", Document(Paragraph(PlainText("the "), Italic(List(PlainText("x"))))))
  )

  test("wiki is the default syntax") {
    right(Scaladoc.fromString(comment)) mustBe expected
    right(Scaladoc.fromString(comment, syntax = CommentSyntax.Wiki)) mustBe expected
  }

  test("fromDocstring takes the syntax too") {
    right(Scaladoc.fromDocstring(comment, syntax = CommentSyntax.Wiki)) mustBe expected
    right(Scaladoc.fromDocstring(comment, strict = true, syntax = CommentSyntax.Wiki)) mustBe expected
  }

  test("the syntax applies to stripped comment bodies") {
    val body = "Some '''bold'''.\n\n@param x the ''x''"
    right(Scaladoc.fromString(body, hasTags = false, syntax = CommentSyntax.Wiki)) mustBe
      right(Scaladoc.fromString(body, hasTags = false))
  }
}
