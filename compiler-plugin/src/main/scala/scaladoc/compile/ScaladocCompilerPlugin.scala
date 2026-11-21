package scaladoc.compile

import scala.tools.nsc.Global
import scala.tools.nsc.plugins.Plugin


class ScaladocCompilerPlugin(val global: Global) extends Plugin {

  val name = "scaladoc-compiler-plugin"

  val description = "Embeds Scaladoc into class bytecode"

  /** Which classes get their scaladoc embedded; set from the plugin options in `init`. */
  private[compile] var filter: EmbedFilter = EmbedFilter.All

  override def init(options: List[String], error: String => Unit): Boolean =
    EmbedFilter.parse(options) match {
      case Right(f) => filter = f; true
      case Left(err) => error(s"$name: $err"); false
    }

  override val optionsHelp: Option[String] = Some(
    s"""  -P:$name:include:path:<glob>     embed only classes from source files matching the glob
       |  -P:$name:exclude:path:<glob>     don't embed classes from source files matching the glob
       |  -P:$name:include:class:<pattern> embed only classes whose fully-qualified name matches
       |  -P:$name:exclude:class:<pattern> don't embed classes whose fully-qualified name matches
       |  Options can be repeated. Without include options every class is included; excludes win.
       |  Class patterns: `*` within a name segment, `**` across segments, `**.` zero or more packages.""".stripMargin)

  val components = new ScaladocComponent(this, global) :: Nil
}
