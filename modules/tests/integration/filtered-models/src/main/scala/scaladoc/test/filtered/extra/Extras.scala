package scaladoc.test.filtered.extra

/** Included by class name
  *
  * @param k the k
  */
case class KeptExtra(k: Int)

/** Not included
  *
  * @param d the d
  */
case class DroppedExtra(d: Int)

/** Not included: only its nested class is
  *
  * @param b the b
  */
case class Box(b: Int) {

  /** Included by class name, nested in a class
    *
    * @param i the i
    */
  case class Inner(i: Int)
}

/** Included by class name
  *
  * @param o the o
  */
case class KeptOuter(o: Int) {

  /** Not included: `Kept*` doesn't match across the dot
    *
    * @param n the n
    */
  case class Nested(n: Int)
}

/** An object, included by class name */
object KeptObject

/** An object, not included */
object DroppedObject
