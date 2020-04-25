package scaladocx.parse


trait Log {

  def info(x: String): Unit
}

object Log {

  final case object Noop extends Log {
    override def info(x: String): Unit = ()
  }

  final case object ConsoleOut extends Log {
    override def info(x: String): Unit = Console.out.println(x)
  }

  final case object ConsoleErr extends Log {
    override def info(x: String): Unit = Console.err.println(x)
  }
}
