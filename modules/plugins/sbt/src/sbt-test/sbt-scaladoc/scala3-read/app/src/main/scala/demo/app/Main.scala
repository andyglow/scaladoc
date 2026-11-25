package demo.app

import demo.model.User
import scaladoc._

object Main {
  def main(args: Array[String]): Unit = {
    val user = Scaladoc.of[User]
    require(user.exists(_.textParams.get("name").contains("the name")), s"User: $user")
    // a parent's define, from the parent's TASTy
    val admin = Scaladoc.of[demo.model.Admin].map(_.textDescriptions.mkString)
    require(admin.contains("An entity of the system, in its own words: its comment uses its parent's define."), s"Admin: $admin")
    // objects, whose comments TASTy has on their classes, which `T.type` alone doesn't read
    val registry = Scaladoc.of[demo.model.Registry.type].map(_.textDescriptions.mkString)
    require(registry.contains("Where the users are."), s"Registry: $registry")
    val guest = Scaladoc.of[demo.model.Guest.type].map(_.textDescriptions.mkString)
    require(guest.contains("The guest, who needs no account."), s"Guest: $guest")
  }
}
