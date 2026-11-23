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

class OfScala3Spec extends FSpec {

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
