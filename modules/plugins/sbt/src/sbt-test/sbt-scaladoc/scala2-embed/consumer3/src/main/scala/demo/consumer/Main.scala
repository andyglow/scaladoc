package demo.consumer

import scaladoc._

// uses a class whose scaladoc the compiler plugin embedded, and reads that scaladoc: on Scala 3, from the annotation
object Main {
  def main(args: Array[String]): Unit = {
    println(demo.model.User("someone").copy(name = "someone else"))
    val doc = Scaladoc.of[demo.model.User]
    assert(doc.map(_.textParams) == Some(Map("name" -> "the name")), s"Scaladoc.of[User]: $doc")
    println("consumer3: the embedded scaladoc of a 2.13 class, read on Scala 3: ok")
  }
}
