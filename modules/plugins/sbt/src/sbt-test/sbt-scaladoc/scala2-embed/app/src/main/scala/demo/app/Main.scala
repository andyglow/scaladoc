package demo.app

import demo.model.User
import demo.model.internal.Secret
import scaladoc._

object Main {
  def main(args: Array[String]): Unit = {
    val user = Scaladoc.of[User]
    require(user.exists(_.textParams.get("name").contains("the name")), s"User: $user")
    require(Scaladoc.of[Secret].isEmpty, s"Secret: ${Scaladoc.of[Secret]}")
    // a parent's define, from the parent's embedded scaladoc
    val admin = Scaladoc.of[demo.model.Admin].map(_.textDescriptions.mkString)
    require(admin.contains("An entity of the system, in its own words: its comment uses its parent's define."), s"Admin: $admin")
  }
}
