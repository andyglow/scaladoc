package scaladoc.compile

import java.io.File

import scaladoc.Scaladoc
import scaladoc.annotation.ScaladocCarrier
import scaladoc.utils.SourceCodeUtils

import scala.tools.nsc
import nsc.Global
import nsc.plugins.PluginComponent
import nsc.ast.TreeDSL
import nsc.transform.{Transform, TypingTransformers}
import scala.collection.immutable.ListMap


class ScaladocComponent(
  plugin: ScaladocCompilerPlugin,
  val global: Global) extends PluginComponent with Transform with TypingTransformers with TreeDSL with CrossVersionSupport  {
  import global._

  override val runsAfter  = "parser" :: Nil
  override val runsBefore = "namer" :: Nil
  override val phaseName  = plugin.name

  // make sure compiler class path contains plugin and all prerequisite entries in it.
  // Done once, when the phase first runs: on scala 2.13 extending the class path while the plugin is being
  // instantiated (as it used to be done) fails with an NPE, as there is no compiler run yet.
  private lazy val extendCompilerClassPath: Unit = {
    val entries = global.settings.plugin.value flatMap { x => if (x.contains(':')) x.split(':').toList else List(x) }
    global.extendCompilerClassPath(entries.map(e => new File(e).toURI.toURL): _*)
  }

  override def newTransformer(unit: CompilationUnit): Trans = {
    extendCompilerClassPath
    new Trans(unit)
  }

  class Trans(unit: CompilationUnit) extends TypingTransformer(unit) {
    private val carrierTpe = typeOf[ScaladocCarrier]
    private val filter     = plugin.filter
    private val path       = unit.source.file.path

    // enclosing packages and objects, innermost first. This phase runs before the namer, so there are no symbols
    // to get the fully-qualified name from.
    private var owners: List[String] = Nil

    private def withOwner[T](name: String)(f: => T): T = {
      val saved = owners
      owners = name :: owners
      try f finally owners = saved
    }

    private def refName(ref: Tree): String = ref match {
      case Select(qual, name) => refName(qual) + "." + name.decoded
      case Ident(name)        => name.decoded
      case other              => other.toString
    }

    private def fullName(name: Name): String = (name.decoded :: owners).reverse.mkString(".")

    // skip the whole compile unit if none of its classes can be embedded
    override def transformUnit(unit: CompilationUnit): Unit =
      if (filter.acceptsFile(path)) super.transformUnit(unit)

    override def transform(tree: global.Tree): global.Tree = {
      tree match {
        case pkg: PackageDef if pkg.name == nme.EMPTY_PACKAGE_NAME =>
          super.transform(pkg)
        case pkg: PackageDef =>
          withOwner(refName(pkg.pid))(super.transform(pkg))
        case obj: ModuleDef =>
          withOwner(obj.name.decoded)(super.transform(obj))
        case cls @ ClassDef(_, name, _, _) if !filter.accepts(path, fullName(name)) =>
          cls
        case cls @ ClassDef(mods, name, tparams, impl) =>
          val scd = for {
            str <- SourceCodeUtils.extractComment(tree.pos)
            scd <- Scaladoc.fromString(str, strict = true) match {
                     case Left(err)  =>
                       reportError(tree.pos, err.getMessage, tree.symbol)
                       Some(tree)
                     case Right(doc) =>
//                       inform(tree.pos, s"Scaladoc AST {\n${doc.pseudoCode}}")
                       Some {
                         val Modifiers(flags, privateWithin, annos) = mods
                         val newAnno = Annotation(
                           carrierTpe,
                           Nil,
                           ListMap(
                             TermName("text") -> LiteralAnnotArg(Constant(str)),
                             TermName("tags") -> LiteralAnnotArg(Constant(true)))).tree

                         val effectiveMods = Modifiers(
                           flags,
                           privateWithin,
                           annos :+ newAnno)

                         val res = cls.copy(mods = effectiveMods).updateAttachment(doc)
//                         inform(showRaw(effectiveMods))
////                         inform(showCode(newAnno))
//                         inform("---\n"+showCode(res)+"\n---")

                         // scala 2.11 requirement
                         res.setPos(tree.pos)
                       }
                    }
          } yield scd

          scd getOrElse tree
        case _ =>
          super.transform(tree)
      }
    }
  }
}