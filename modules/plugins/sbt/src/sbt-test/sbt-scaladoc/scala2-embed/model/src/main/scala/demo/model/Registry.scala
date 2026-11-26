package demo.model

/** Where the users are. */
object Registry

/** The guest, who needs no account. */
case object Guest

/** The root $kind: an object's comment uses its parent's define too. */
case object Root extends Entity
