package demo.model

/** Something in the system.
  *
  * @define kind entity
  */
trait Entity

/** An $kind of the system, in its own words: its comment uses its parent's define. */
case class Admin(name: String) extends Entity
