//> using dep demo:model_2.13:1.0
//> using dep io.github.andyglow::scaladoc-parser:0.5.0

package demo.consumer

import scaladoc._

// uses a 2.13 class whose scaladoc the compiler plugin embedded, as a Scala 3 project uses a 2.13 library: it compiles
// because the model's POM brings scaladoc-annotation along. And reads that scaladoc, from the annotation
object Main {
  def main(args: Array[String]): Unit = {
    val doc = Scaladoc.of[demo.model.User]
    assert(doc.map(_.textParams) == Some(Map("name" -> "the name")), s"Scaladoc.of[User]: $doc")
    println("consumer3: " + demo.model.User("someone").name)
  }
}
