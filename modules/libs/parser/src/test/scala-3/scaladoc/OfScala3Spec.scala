package scaladoc

import scala.compiletime.testing.typeCheckErrors

/** A documented Scala 3 enum.
  *
  * @param mass the mass
  */
enum Planet(mass: Double) {

  /** The third planet. */
  case Earth extends Planet(5.97e24)

  case Mars extends Planet(6.42e23)
}

object Misdocumented {

  /** Misaligned border.
    *
      * @param a the a
    */
  case class Misaligned(a: Int)
}

/** Some **bold**, `code` and a [link](https://x.y): Markdown, Scala 3's default.
  *
  * @param x the *x*
  */
class MarkdownModel(x: Int)

/** Some '''bold''' text, in wiki syntax. */
class WikiModel

/** Some '''bold''' text, in wiki syntax, which it says.
  *
  * @syntax wiki
  */
class SaysWiki

// as the Scala 2 compiler plugin embeds a Scala 2 class's scaladoc, which Scala 3 doesn't see as a doc comment
@scaladoc.annotation.ScaladocCarrier(text = "/** An '''embedded''' one.\n  *\n  * @param x the x\n  */", tags = true)
class Embedded(x: Int)

class OfScala3Spec extends FSpec {
  import Markup._

  test("Markdown, Scala 3's default") {
    Scaladoc.of[MarkdownModel].map(_.descriptions) mustBe Some(List(Document(Paragraph(
      PlainText("Some "), Bold("bold"), PlainText(", "), Monospace("code"), PlainText(" and a "), Link("https://x.y", "link"),
      PlainText(": Markdown, Scala 3's default.")))))
    Scaladoc.of[MarkdownModel].map(_.params) mustBe Some(Map("x" -> Document(Paragraph(PlainText("the "), Italic("x")))))
  }

  test("wiki, when a given in scope says so") {
    import scaladoc.syntax.wiki.given
    Scaladoc.of[WikiModel].map(_.descriptions) mustBe Some(List(Document(Paragraph(
      PlainText("Some "), Bold("bold"), PlainText(" text, in wiki syntax.")))))
  }

  test("Markdown, when a given in scope says so") {
    import scaladoc.syntax.markdown.given
    Scaladoc.of[WikiModel].map(_.textDescriptions) mustBe Some(Seq("Some '''bold''' text, in wiki syntax."))
  }

  test("wiki, when the comment says so, without a given") {
    Scaladoc.of[SaysWiki].map(_.descriptions) mustBe Some(List(Document(Paragraph(
      PlainText("Some "), Bold("bold"), PlainText(" text, in wiki syntax, which it says.")))))
    Scaladoc.of[SaysWiki].map(_.tags.size) mustBe Some(1) // `@syntax` isn't a tag of its own
  }

  test("a Scala 2 class's embedded scaladoc, in wiki syntax") {
    Scaladoc.of[Embedded].map(_.descriptions) mustBe Some(List(Document(Paragraph(
      PlainText("An "), Bold("embedded"), PlainText(" one.")))))
    Scaladoc.of[Embedded].map(_.textParams) mustBe Some(Map("x" -> "the x"))
  }


  test("enum") {
    val doc = Scaladoc.of[Planet]
    doc.map(_.textDescriptions) mustBe Some(Seq("A documented Scala 3 enum."))
    doc.map(_.textParams) mustBe Some(Map("mass" -> "the mass"))
  }

  test("misaligned doc comment is a compile error, like on Scala 2") {
    val errors = typeCheckErrors("scaladoc.Scaladoc.of[scaladoc.Misdocumented.Misaligned]").map(_.message).mkString
    errors must include("Error extracting scaladoc from comment")
    errors must include("Broken indentation")
  }
}
