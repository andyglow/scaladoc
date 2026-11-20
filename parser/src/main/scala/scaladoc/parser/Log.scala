package scaladoc.parser


trait Log {

  def info(x: String): Unit
}

object Log {

  case object Noop extends Log {
    override def info(x: String): Unit = ()
  }

  case object ConsoleOut extends Log {
    override def info(x: String): Unit = Console.out.println(x)
  }

  case object ConsoleErr extends Log {
    override def info(x: String): Unit = Console.err.println(x)
  }
}
