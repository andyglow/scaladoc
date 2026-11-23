package demo.app

import demo.model.User
import demo.model.internal.Secret
import scaladoc._

// reads the scaladoc of another module's classes: embedded into their bytecode on Scala 2, from TASTy on Scala 3
object Main {
  def main(args: Array[String]): Unit = {
    val user = Scaladoc.of[User]
    require(user.exists(_.textParams.get("name").contains("the name")), s"User: $user")
    // excluded from embedding on Scala 2; Scala 3 has no compiler plugin, so nothing is excluded there
    val secret = Scaladoc.of[Secret]
    require(secret.isEmpty == (args.headOption.contains("excluded")), s"Secret: $secret")
    println("scaladoc: ok")
  }
}
