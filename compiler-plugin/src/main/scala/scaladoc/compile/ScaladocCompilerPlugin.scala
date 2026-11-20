package scaladoc.compile

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin


class ScaladocCompilerPlugin(val global: Global) extends Plugin {

  val name = "scaladoc-compiler-plugin"

  val description = "Embeds Scaladoc into class bytecode"

  val components = new ScaladocComponent(this, global) :: Nil
}
