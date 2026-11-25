package demo.consumer

import scaladoc._

// uses a class whose scaladoc the compiler plugin embedded, and reads that scaladoc: on Scala 3, from the annotation
object Main {
  def main(args: Array[String]): Unit = {
    println(demo.model.User("someone").copy(name = "someone else"))
    val doc = Scaladoc.of[demo.model.User]
    assert(doc.map(_.textParams) == Some(Map("name" -> "the name")), s"Scaladoc.of[User]: $doc")
    // and a parent's define, from the parent's embedded scaladoc too
    val admin = Scaladoc.of[demo.model.Admin].map(_.textDescriptions.mkString)
    assert(admin.contains("An entity of the system, in its own words: its comment uses its parent's define."), s"Admin: $admin")
    // objects, whose scaladoc the compiler plugin embeds too
    val registry = Scaladoc.of[demo.model.Registry.type].map(_.textDescriptions.mkString)
    assert(registry.contains("Where the users are."), s"Registry: $registry")
    val guest = Scaladoc.of[demo.model.Guest.type].map(_.textDescriptions.mkString)
    assert(guest.contains("The guest, who needs no account."), s"Guest: $guest")
    val root = Scaladoc.of[demo.model.Root.type].map(_.textDescriptions.mkString)
    assert(root.contains("The root entity: an object's comment uses its parent's define too."), s"Root: $root")
    println("consumer3: the embedded scaladoc of a 2.13 class, read on Scala 3: ok")
  }
}
