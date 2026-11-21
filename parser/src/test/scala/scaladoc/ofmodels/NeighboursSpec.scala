package scaladoc.ofmodels

// Documented and undocumented declarations side by side: a doc comment belongs only to the declaration right
// after it (whitespace, annotations, modifiers and ordinary comments may sit in between), never to a later one.
// The models share the file with the spec for the same reason as in OfSpec.scala: on Scala 2 they must be
// compiled in the same run as the `Scaladoc.of` calls (an incremental compile may recompile the spec alone).

final class marker(val value: String = "") extends scala.annotation.StaticAnnotation

/** Documented first. */
case class DocFirst(a: Int)

case class UndocAfterDoc(s: String)

object WithMethod {

  /** Documented method.
    *
    * @param x the x
    */
  def m(x: Int): Int = x
}

case class UndocAfterMethod(s: String)

/** Annotated. */ @marker final case class Annotated(a: Int)

/** Multi-line annotations.
  *
  * @param a the a
  */
@marker(
  value = "a (tricky) ) value */ /** with markers"
)
@SerialVersionUID(
  1L
)
sealed abstract class MultiLineAnnotated(a: Int)

/** Blank line after. */

case class BlankLineAfter(a: Int)

/** Line comment after. */
// a line comment
case class LineCommentAfter(a: Int)

/** Block comment after. */
/* a block comment */
case class BlockCommentAfter(a: Int)

object StringHolder {
  val s: String = "/** not a doc comment */"
}
case class AfterStringLiteral(a: Int)

// Scala 2 must agree with Scala 3's `Symbol.docstring`
class NeighboursSpec extends scaladoc.FSpec {
  import scaladoc._

  private def descriptions(doc: Option[Scaladoc]): Option[Seq[String]] = doc.map(_.textDescriptions)

  test("an undocumented class doesn't take the doc comment of the class or method above it") {
    descriptions(Scaladoc.of[DocFirst]) mustBe Some(Seq("Documented first."))
    Scaladoc.of[UndocAfterDoc] mustBe None
    Scaladoc.of[UndocAfterMethod] mustBe None
  }

  test("annotations and modifiers between the doc comment and the declaration") {
    descriptions(Scaladoc.of[Annotated]) mustBe Some(Seq("Annotated."))
    descriptions(Scaladoc.of[MultiLineAnnotated]) mustBe Some(Seq("Multi-line annotations."))
    Scaladoc.of[MultiLineAnnotated].map(_.textParams) mustBe Some(Map("a" -> "the a"))
  }

  test("blank lines and ordinary comments between the doc comment and the declaration") {
    descriptions(Scaladoc.of[BlankLineAfter]) mustBe Some(Seq("Blank line after."))
    descriptions(Scaladoc.of[LineCommentAfter]) mustBe Some(Seq("Line comment after."))
    descriptions(Scaladoc.of[BlockCommentAfter]) mustBe Some(Seq("Block comment after."))
  }

  test("a doc comment inside a string literal is not a doc comment") {
    Scaladoc.of[AfterStringLiteral] mustBe None
  }
}
