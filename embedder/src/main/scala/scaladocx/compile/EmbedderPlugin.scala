package scaladocx.compile

import java.io.File

import scala.tools.nsc
import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin


class EmbedderPlugin(val global: Global) extends Plugin {

  // make sure compiler class path contains plugin and all prerequisite entries in it
  global.settings.plugin.value flatMap { x => if (x.contains(':')) x.split(':').toList else List(x) } foreach { e =>
    global.extendCompilerClassPath(new File(e).toURL)
  }

  val name = "scaladoc-embedder"

  val description = "Embeds Scaladocs into AST"

  val components = new EmbedderComponent(this, global) :: Nil
}