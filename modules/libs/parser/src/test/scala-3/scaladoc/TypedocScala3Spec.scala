package scaladoc

import scala.compiletime.testing.typeCheckErrors

/** What a robot can do. */
enum Command {

  /** Stops. */
  case Stop

  /** Moves.
    *
    * @param steps how far
    */
  case Move(steps: Int, fast: Boolean)

  case Turn
}

/** One of its variants is misdocumented. */
sealed trait Misdocumenting

object Misdocumenting {

  /** Misaligned border.
    *
      * @param a the a
    */
  case class Misaligned(a: Int) extends Misdocumenting
}

class TypedocScala3Spec extends FSpec {
  import Markup.*

  private def doc(text: String): Option[Scaladoc] = Some(Scaladoc(Tag.Description(Document(Paragraph(PlainText(text))))))

  test("an enum: its cases, values and classes, in declaration order") {
    Typedoc.of[Command] mustBe Typedoc.Sum(
      "Command",
      doc("What a robot can do."),
      List(
        Typedoc.Singleton("Stop", doc("Stops.")),
        Typedoc.Product(
          "Move",
          Scaladoc.of[Command.Move],
          List(Typedoc.Field("steps", Some(Document(Paragraph(PlainText("how far"))))), Typedoc.Field("fast", None))),
        Typedoc.Singleton("Turn", None)
      )
    )
  }

  test("an enum with parameters: they aren't its cases' fields") {
    Typedoc.of[Planet] mustBe Typedoc.Sum(
      "Planet",
      Scaladoc.of[Planet],
      List(Typedoc.Singleton("Earth", doc("The third planet.")), Typedoc.Singleton("Mars", None)))
  }

  test("a malformed comment anywhere in the tree fails the compile") {
    val errors = typeCheckErrors("scaladoc.Typedoc.of[scaladoc.Misdocumenting]").map(_.message).mkString
    errors must include("Error extracting scaladoc from comment")
    errors must include("Broken indentation")
  }
}
