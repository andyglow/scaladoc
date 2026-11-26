package demo.model

/** A role a user has. */
enum Role {

  /** Reads. */
  case Reader

  /** Writes, to some places.
    *
    * @param places where
    */
  case Writer(places: List[String])
}

/** How a user signs in. */
sealed trait Login

object Login {

  /** With a password.
    *
    * @param user the user's name
    */
  final case class Password(user: String) extends Login

  /** Without signing in. */
  case object Anonymous extends Login
}
