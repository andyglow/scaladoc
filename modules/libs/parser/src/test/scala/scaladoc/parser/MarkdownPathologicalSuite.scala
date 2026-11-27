package scaladoc.parser

import org.scalatest.funsuite.AnyFunSuite

/** Markdown's pathological inputs, as `PathologicalInput` has them. */
class MarkdownPathologicalSuite extends AnyFunSuite with PathologicalInput {

  // words, emphasis, code and links, in paragraphs
  protected def ordinary(length: Int): String =
    ("Some *words*, a `code` span and [a link](https://x.y).\n\n" * (length / 55 + 1)).take(length)

  private val cases: List[(String, String)] = List(
    "nested strong and emphasis"            -> (r("*a **a ", N / 6) + "b" + r(" a** a*", N / 6)),
    "emphasis closers without openers"      -> r("a_ "),
    "emphasis openers without closers"      -> r("_a "),
    "link closers without openers"          -> r("a]"),
    "link openers without closers"          -> r("[a"),
    "mismatched openers and closers"        -> r("*a_ "),
    "openers and closers, multiples of 3"   -> ("a**b" + r("c* ")),
    "link openers and emphasis closers"     -> r("[ a_"),
    "`[ (](` repeated"                      -> r("[ (]("),
    "nested brackets"                       -> (r("[", N) + "a" + r("]", N)),
    "nested block quotes"                   -> (r("> ", N / 2) + "a"),
    "backtick runs without closers"         -> (1 to 800).map(i => "e" + "`" * i).mkString,
    "unclosed links, pointy destination"    -> r("[a](<b"),
    "unclosed links"                        -> r("[a](b"),
    "unclosed HTML comments"                -> r("<!-- "),
    "an HTML tag's attributes without a `>`" -> ("<a " + r("x ")),
    "spaces in an HTML tag without a `>`"   -> ("<a" + r(" ")),
    "unclosed wiki links"                   -> r("[[a"),
    "almost email addresses"                -> r("a@"),
    "almost URLs"                           -> r("www.a "),
    "entities without their `;`"            -> r("&amp"),
    "reference definitions"                 -> (r("[a]: b\n", N / 6) + "[a]"),
    "many lines in a paragraph"             -> r("a\n"),
    "a long table"                          -> ("| a | b |\n|---|---|\n" + r("| x | y |\n", N / 10)),
    "footnote references without closers"   -> r("[^a"),
    "many footnotes and references"         -> (r("[^a]: b\n", N / 8) + r("x[^a] ", N / 6))
  )

  // through the tag layer too, which looks for each fence's closer
  test("many fences without closers, in a comment with tags") {
    val comment = "/** " + r("```x\n  * ", N / 10) + "\n  * @param a the a\n  */"
    def parse(c: String) = ScaladocParser.fromString(c, syntax = scaladoc.CommentSyntax.Markdown)
    assert(parse(comment).map(_.textParams) == Right(Map("a" -> "the a")))
    quick(comment)(c => parse(if (c eq comment) c else "/** " + c.replace("\n", "\n  * ") + " */"))
  }

  cases.foreach { case (name, input) =>
    test(s"$name (${input.length} characters) parses quickly") {
      quick(input)(MarkupSyntax.Markdown.document)
    }
  }
}
