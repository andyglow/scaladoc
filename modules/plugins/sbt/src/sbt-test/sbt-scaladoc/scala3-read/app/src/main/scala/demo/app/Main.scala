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
    // an enum and a sealed trait, as trees, from TASTy: in declaration order
    val role = show(Typedoc.of[demo.model.Role])
    require(role == "Role(A role a user has.)[Reader(Reads.), Writer(Writes, to some places.){places: where}]", s"Role: $role")
    val login = show(Typedoc.of[demo.model.Login])
    require(
      login == "Login(How a user signs in.)[Password(With a password.){user: the user's name}, Anonymous(Without signing in.)]",
      s"Login: $login")
  }

  // the tree, one line: names, descriptions and fields' docs
  private def show(t: Typedoc): String = {
    def text(doc: Option[Scaladoc]) = doc.map(_.textDescriptions.mkString).getOrElse("-")
    t match {
      case Typedoc.Sum(name, doc, variants) => s"$name(${text(doc)})[${variants.map(show).mkString(", ")}]"
      case Typedoc.Product(name, doc, fields) =>
        s"$name(${text(doc)}){${fields.map(f => f.name + ": " + f.doc.fold("-")(_.trimmed.plainString)).mkString(", ")}}"
      case Typedoc.Singleton(name, doc) => s"$name(${text(doc)})"
      case Typedoc.Plain(name, doc)     => s"$name(${text(doc)})"
    }
  }
}
