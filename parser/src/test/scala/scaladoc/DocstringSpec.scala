package scaladoc

import scaladoc.Markup._
import scaladoc.Tag._
import scaladoc.parser.ScaladocParser

// Parsing the raw text Scala 3's `Symbol.docstring` returns: `/** ... */` markers included, the first line
// without its indentation, the following lines with theirs.
class DocstringSpec extends FSpec {

  private def right(x: Either[ScaladocException, Scaladoc]): Scaladoc = x.fold(err => fail(err.getMessage), identity)

  // verbatim from scala-jsonschema's macro spike (`Symbol.docstring` of a top-level case class)
  private val topLevel =
    "/** Documented class.\n  *\n  * Second line of description.\n  *\n  * @param x the x param\n  */"

  // a case class nested in an object (comment indented by 2 in the source)
  private val nested     = "/** Nested class.\n    *\n    * @param a the a\n    *   continued\n    * @param b the b\n    */"
  private val nestedSrc  = "  " + nested // what Scala 2 extracts from the source: indentation kept
  private val misaligned = "/** Misaligned.\n    *\n      * @param a the a\n    */"

  test("top-level docstring") {
    val doc = right(Scaladoc.fromString(topLevel))
    doc mustBe Scaladoc(
      Description(Document(Paragraph(PlainText("Documented class.")), Paragraph(PlainText("Second line of description.")))),
      Param("x", Document(Paragraph(PlainText("the x param"))))
    )
    doc.textDescriptions mustBe Seq("Documented class.\nSecond line of description.")
    doc.textParams mustBe Map("x" -> "the x param")
    right(Scaladoc.fromDocstring(topLevel)) mustBe doc
    right(Scaladoc.fromDocstring(topLevel, strict = true)) mustBe doc
  }

  test("nested docstring parses like the Scala 2 source text") {
    val expected = right(Scaladoc.fromString(nestedSrc, strict = true))
    expected.textParams mustBe Map("a" -> "the a\ncontinued", "b" -> "the b")

    right(Scaladoc.fromString(nested)) mustBe expected
    right(Scaladoc.fromDocstring(nested)) mustBe expected
    right(Scaladoc.fromDocstring(nested, strict = true)) mustBe expected
  }

  test("strict fromString rejects a nested docstring, strict fromDocstring does not") {
    Scaladoc.fromString(nested, strict = true) mustBe a[Left[_, _]]
    Scaladoc.fromString(nested, strict = true).left.toOption.get mustBe a[BrokenIndentation]
  }

  test("strict fromDocstring still rejects a misaligned border") {
    Scaladoc.fromDocstring(misaligned) mustBe a[Right[_, _]]
    Scaladoc.fromDocstring(misaligned, strict = true).left.toOption.get mustBe a[BrokenIndentation]
  }

  test("javadoc style") {
    val top    = "/**\n * Javadoc style.\n *\n * @param a the a\n */"
    val nested = "/**\n     * Javadoc style.\n     *\n     * @param a the a\n     */"
    val doc    = right(Scaladoc.fromDocstring(top, strict = true))
    doc.textDescriptions mustBe Seq("Javadoc style.")
    doc.textParams mustBe Map("a" -> "the a")
    right(Scaladoc.fromDocstring(nested, strict = true)) mustBe doc
    right(Scaladoc.fromString(nested)) mustBe doc
  }

  test("one-liner") {
    val doc = right(Scaladoc.fromDocstring("/** One liner. */", strict = true))
    doc.textDescriptions mustBe Seq("One liner.")
    doc.params mustBe empty
  }

  test("non-scaladoc comments are rejected") {
    Scaladoc.fromDocstring("/* block */") mustBe Left(NonScaladocStyle(Style.Block))
    Scaladoc.fromDocstring("") mustBe Left(EmptyInput)
  }

  test("restoreIndentation") {
    ScaladocParser.restoreIndentation("/** a */") mustBe "/** a */"
    ScaladocParser.restoreIndentation("/** a\n  */") mustBe "/** a\n  */"
    ScaladocParser.restoreIndentation("/** a\n      */") mustBe "    /** a\n      */"
    ScaladocParser.restoreIndentation("/**\n * a\n */") mustBe "/**\n * a\n */"
    ScaladocParser.restoreIndentation("  /** a\n    */") mustBe "  /** a\n    */" // already indented
  }
}
