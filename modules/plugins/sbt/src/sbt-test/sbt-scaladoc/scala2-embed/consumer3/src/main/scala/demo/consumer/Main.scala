package demo.consumer

// uses a class whose scaladoc the compiler plugin embedded, with no scaladoc of its own
object Main {
  def main(args: Array[String]): Unit = println(demo.model.User("someone").copy(name = "someone else"))
}
