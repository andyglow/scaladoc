package scaladoc

/** One of its variants is misdocumented. */
sealed trait Misdocumenting

object Misdocumenting {

  /** Misaligned border.
    *
      * @param a the a
    */
  case class Misaligned(a: Int) extends Misdocumenting
}

// as the compiler plugin embeds a misaligned comment
@scaladoc.annotation.ScaladocCarrier(text = "/** Misaligned border.\n  *\n    * @param a the a\n  */", tags = true)
class MisalignedCarrier(a: Int)

class TypedocScala2Spec extends FSpec {

  test("a hierarchy from classfiles: by name, as they have no positions") {
    // `scaladoc-core` is compiled before these tests, and without the compiler plugin, so it has no scaladoc here
    Typedoc.of[Markup.OrderedList.Style] mustBe Typedoc.Sum(
      "Style",
      None,
      List("Decimal", "LowerAlpha", "LowerRoman", "UpperAlpha", "UpperRoman").map(Typedoc.Singleton(_, None)))
  }

  test("a malformed comment anywhere in the tree fails the compile") {
    assertDoesNotCompile("scaladoc.Typedoc.of[scaladoc.Misdocumenting]")
  }

  test("a malformed embedded comment fails the compile") {
    assertDoesNotCompile("scaladoc.Scaladoc.of[scaladoc.MisalignedCarrier]")
  }
}
