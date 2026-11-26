package scaladoc.test.filtered.model

/** A user
  *
  * @param name the name
  */
case class User(name: String)

object Holder {

  /** Nested in an object
    *
    * @param x the x
    */
  case class Nested(x: Int)
}

/** An object, included by path */
object Users
