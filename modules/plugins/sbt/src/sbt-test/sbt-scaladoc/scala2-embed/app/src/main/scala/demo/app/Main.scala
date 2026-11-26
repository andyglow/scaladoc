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
    // objects, whose scaladoc the compiler plugin embeds too
    val registry = Scaladoc.of[demo.model.Registry.type].map(_.textDescriptions.mkString)
    require(registry.contains("Where the users are."), s"Registry: $registry")
    val guest = Scaladoc.of[demo.model.Guest.type].map(_.textDescriptions.mkString)
    require(guest.contains("The guest, who needs no account."), s"Guest: $guest")
    val root = Scaladoc.of[demo.model.Root.type].map(_.textDescriptions.mkString)
    require(root.contains("The root entity: an object's comment uses its parent's define too."), s"Root: $root")
    // a sealed trait, as a tree, from the embedded scaladoc: a jar has no order, so its variants come by name
    val plan = show(Typedoc.of[demo.model.Plan])
    require(
      plan == "Plan(A subscription plan.)[Free(Free, for everyone.), Lifetime(Paid once.), Monthly(Paid by the month.){price: the price}]",
      s"Plan: $plan")
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
