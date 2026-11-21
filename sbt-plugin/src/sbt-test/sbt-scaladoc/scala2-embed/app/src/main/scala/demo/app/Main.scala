package demo.app

import demo.model.User
import demo.model.internal.Secret
import scaladoc._

object Main {
  def main(args: Array[String]): Unit = {
    val user = Scaladoc.of[User]
    require(user.exists(_.tags.contains(Tag.Param("name", Markup.Document(Markup.PlainText("the name"))))), s"User: $user")
    require(Scaladoc.of[Secret].isEmpty, s"Secret: ${Scaladoc.of[Secret]}")
  }
}
