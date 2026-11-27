package scaladoc.parser

import org.scalatest.funsuite.AnyFunSuite

/** The wiki syntax's pathological inputs, as `PathologicalInput` has them: HTML, code blocks, entities and tables.
  * Unclosed inline markup, like `<b>` without `</b>`, is #203.
  */
class WikiPathologicalSuite extends AnyFunSuite with PathologicalInput {

  // words, italics, code and links, in paragraphs
  protected def ordinary(length: Int): String =
    ("Some ''words'', a `code` span and [[scala.Option an option]].\n\n" * (length / 59 + 1)).take(length)

  private val cases: List[(String, String)] = List(
    "<pre> without </pre>"                       -> r("<pre> x "),
    "<pre> tags without their `>`"               -> r("<pre "),
    "<code> tags without their `>`, in a <pre>"  -> ("<pre>" + r("<code ") + "</pre>"),
    "comments without -->"                       -> r("<!-- x "),
    "{{{ without }}}"                            -> r("{{{ x "),
    "{{{ without }}}, after HTML"                -> r("<p>{{{ x "),
    "block tags without their `>`"               -> r("<p <h1 <li <ul "),
    "an inline tag's attributes without a `>`"   -> ("<b " + r("x ")),
    "inline tags without their `>`"              -> r("<b x "),
    "an href without its closing quote"          -> ("<a href=\"" + r("x ") + ">"),
    "entities without their `;`"                 -> r("&amp &#1 &#x1 "),
    "many lines in a paragraph"                  -> r("a\n"),
    "a long table"                               -> ("| a | b |\n|:-:|--:|\n" + r("| x | y |\n", N / 10)),
    "a table's delimiter cells, without the row" -> r("| :-: | --: |\n", N / 14)
  )

  // through the tag layer too, which looks for each code block's end
  test("many code blocks without their ends, in a comment with tags") {
    val comment = "/** " + r("<pre> x\n  * {{{ y\n  * ", N / 20) + "\n  * @param a the a\n  */"
    def parse(c: String) = ScaladocParser.fromString(c)
    assert(parse(comment).map(_.textParams.keySet) == Right(Set.empty))
    quick(comment)(c => parse(if (c eq comment) c else "/** " + c.replace("\n", "\n  * ") + " */"))
  }

  cases.foreach { case (name, input) =>
    test(s"$name (${input.length} characters) parses quickly") {
      quick(input)(ParseMarkup(_: String))
    }
  }
}
