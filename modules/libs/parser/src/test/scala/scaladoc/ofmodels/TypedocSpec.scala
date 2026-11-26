package scaladoc.ofmodels

// The models are in this file for the same reason as `OfSpec`'s: Scala 2 reads their comments from the source text.
// Their comments are plain text, which wiki and Markdown read the same.

/** A shape. */
sealed trait Shape

object Shape {

  /** A circle.
    *
    * @param radius the radius
    * @param diameter not a field
    */
  final case class Circle(radius: Double) extends Shape

  /** A square, with an undocumented side. */
  final case class Square(side: Double) extends Shape

  /** No shape at all. */
  case object Empty extends Shape

  /** A polygon. */
  sealed trait Polygon extends Shape

  /** A triangle.
    *
    * @param a the first side
    * @param c the third side
    */
  final case class Triangle(a: Double, b: Double, c: Double) extends Polygon

  final case class Hexagon(side: Double) extends Polygon

  /** Neither a case class nor an object. */
  class Blob extends Shape
}

/** An animal, or one of its kinds. */
sealed class Animal

/** A dog. */
class Dog extends Animal

class Cat extends Animal

class TypedocSpec extends scaladoc.FSpec {
  import scaladoc._
  import scaladoc.Markup._

  private def doc(text: String): Option[Scaladoc] = Some(Scaladoc(Tag.Description(Document(Paragraph(PlainText(text))))))

  private def markup(text: String): Option[Markup] = Some(Document(Paragraph(PlainText(text))))

  test("a sealed trait: its variants, in declaration order, with nested sums") {
    Typedoc.of[Shape] mustBe Typedoc.Sum(
      "Shape",
      doc("A shape."),
      List(
        Typedoc.Product("Circle", Scaladoc.of[Shape.Circle], List(Typedoc.Field("radius", markup("the radius")))),
        Typedoc.Product("Square", doc("A square, with an undocumented side."), List(Typedoc.Field("side", None))),
        Typedoc.Singleton("Empty", doc("No shape at all.")),
        Typedoc.Sum(
          "Polygon",
          doc("A polygon."),
          List(
            Typedoc.Product(
              "Triangle",
              Scaladoc.of[Shape.Triangle],
              List(Typedoc.Field("a", markup("the first side")), Typedoc.Field("b", None), Typedoc.Field("c", markup("the third side")))),
            Typedoc.Product("Hexagon", None, List(Typedoc.Field("side", None)))
          )
        ),
        Typedoc.Plain("Blob", doc("Neither a case class nor an object."))
      )
    )
  }

  test("a node's doc is what Scaladoc.of gives") {
    Typedoc.of[Shape.Circle].doc mustBe Scaladoc.of[Shape.Circle]
    Typedoc.of[Shape.Circle].doc.map(_.textParams) mustBe Some(Map("radius" -> "the radius", "diameter" -> "not a field"))
  }

  test("a case class") {
    Typedoc.of[Shape.Square] mustBe Typedoc.Product("Square", doc("A square, with an undocumented side."), List(Typedoc.Field("side", None)))
  }

  test("an object") {
    Typedoc.of[Shape.Empty.type] mustBe Typedoc.Singleton("Empty", doc("No shape at all."))
  }

  test("a sealed class that isn't abstract: its subclasses") {
    Typedoc.of[Animal] mustBe Typedoc.Sum("Animal", doc("An animal, or one of its kinds."), List(Typedoc.Plain("Dog", doc("A dog.")), Typedoc.Plain("Cat", None)))
  }

  test("undocumented, and anything else") {
    Typedoc.of[Undocumented] mustBe Typedoc.Product("Undocumented", None, List(Typedoc.Field("a", None)))
    Typedoc.of[SomeTrait] mustBe Typedoc.Plain("SomeTrait", doc("A trait."))
  }
}
