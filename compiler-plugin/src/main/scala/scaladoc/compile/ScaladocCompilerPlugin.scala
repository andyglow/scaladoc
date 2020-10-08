package scaladoc.compile

import java.io.File

import scala.tools.nsc
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin


class ScaladocCompilerPlugin(val global: Global) extends Plugin {

  // make sure compiler class path contains plugin and all prerequisite entries in it
  global.settings.plugin.value flatMap { x => if (x.contains(':')) x.split(':').toList else List(x) } foreach { e =>
    global.extendCompilerClassPath(new File(e).toURL)
  }

  val name = "scaladoc-compiler-plugin"

  val description = "Embeds Scaladoc into class bytecode"

  val components = new ScaladocComponent(this, global) :: Nil
}