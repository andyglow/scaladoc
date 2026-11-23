package scaladoc.parser

import org.scalatest.funsuite.AnyFunSuite

/** Inputs that make a careless Markdown parser quadratic or worse, after cmark's `pathological_tests.py`. Each is big
  * enough for that to take hundreds of times as long as ordinary text of its size, and has to parse in at most 50 times
  * as long (linear cases with big constants, like nested quotes parsed once a level, run at about 20): measured against
  * that, rather than the clock, it holds on a slow machine and with coverage instrumentation.
  */
class MarkdownPathologicalSuite extends AnyFunSuite {

  private val N = 30000

  private def r(s: String, n: Int = N): String = s * n

  // the fastest of a few runs, after a warm-up: a single run is off by a GC pause or a JIT compile, and the baseline's
  // error is multiplied by the factor
  private def nanos(f: => Any): Long = {
    f
    (1 to 3).map { _ =>
      val start = System.nanoTime()
      f
      System.nanoTime() - start
    }.min
  }

  private def ms(nanos: Long): String = f"${nanos / 1e6}%.1f ms"

  // ordinary text of `length` characters: words, emphasis, code and links, in paragraphs
  private def ordinary(length: Int): String =
    ("Some *words*, a `code` span and [a link](https://x.y).\n\n" * (length / 55 + 1)).take(length)

  private def quick(input: String)(parse: String => Any): Unit = {
    val text     = ordinary(input.length)
    val baseline = nanos(parse(text))
    val took     = nanos(parse(input))
    assert(took <= math.max(50000000L, 50 * baseline), s"took ${ms(took)}, ordinary text of its size ${ms(baseline)}")
  }

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
