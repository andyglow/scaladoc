package scaladoc.parser

import org.scalatest.funsuite.AnyFunSuite
import scaladoc.{MarkupTooDeep, UnclosedMarkup}

import scala.util.{Failure, Try}

/** The wiki syntax's pathological inputs, as `PathologicalInput` has them: HTML, code blocks, entities, tables and
  * inline markup.
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
    "a table's delimiter cells, without the row" -> r("| :-: | --: |\n", N / 14),
    // markers that aren't closed are text: each would parse the rest of the paragraph, and the ones in it, again
    "link titles without ]]"                     -> r("[[a b "),
    "markers without their closers"              -> r("__ x ^ x ,, x "),
    "markers and link titles without closers"    -> r("[[a b '' x __ x "),
    "bold and italic without their closers"      -> r("''' x '' x "),
    "unknown javadoc tags without their }"       -> r("{@unknown x ")
  )

  // tags that aren't closed, or nest too deep, fail, and do so right away
  private val failing: List[(String, String, Class[_])] = List(
    ("<b> without </b>", r("<b> x "), classOf[MarkupTooDeep]),
    ("<b> and <i> without their ends", r("<b> x <i> x "), classOf[MarkupTooDeep]),
    ("<a href> without </a>", r("<a href='x'> x "), classOf[MarkupTooDeep]),
    ("<code> without </code>", r("<code> x "), classOf[UnclosedMarkup]),
    ("{@code without }", r("{@code x "), classOf[UnclosedMarkup]),
    ("<b> after <code>s without </code>", r("<code> x ", N / 2) + "<b>", classOf[UnclosedMarkup]),
    ("<b> nested deep, and closed", r("<b>", N / 2) + "x" + r("</b>", N / 2), classOf[MarkupTooDeep]),
    ("{@link labels nested deep", r("{@link a b ", N / 3) + r("}", N / 3), classOf[MarkupTooDeep])
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

  failing.foreach { case (name, input, error) =>
    test(s"$name (${input.length} characters) fails quickly") {
      Try(ParseMarkup(input)) match {
        case Failure(e) => assert(error.isInstance(e), e)
        case other      => fail(s"parsed: $other")
      }
      quick(input)(x => Try(ParseMarkup(x)))
    }
  }
}
