package demo.consumer

// uses a class whose scaladoc the compiler plugin embedded, with no scaladoc of its own
object Main {
  def main(args: Array[String]): Unit = println("consumer3: " + demo.model.User("someone").name)
}
