package scaladoc.test.external

/** External Class 1
  *
  * @param a A Param
  * @param b B Param
  */
case class ExternalClass1(a: String, b: String)

/** External Class 2
  *
  * @param c C Param
  * @param d D Param
  */
case class ExternalClass2(c: String, d: String)

/** External Outer
  *
  * @param a A Param
  */
case class ExternalOuter(a: String) {

  /** Nested in a class
    *
    * @param b B Param
    */
  case class Inner(b: String)
}

/** External Trait */
trait ExternalTrait {

  /** Nested in a trait
    *
    * @param c C Param
    */
  class Member(val c: String) {

    /** Two levels deep
      *
      * @param d D Param
      */
    case class Deeper(d: String)
  }
}

/** External Object */
object ExternalObject

/** External Case Object */
case object ExternalCaseObject

/** External Companion, the class
  *
  * @param a A Param
  */
case class ExternalCompanion(a: String)

/** External Companion, the object */
object ExternalCompanion

object ExternalHolder {

  /** Nested in an object */
  object Inner
}
