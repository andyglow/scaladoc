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

    // enclosing packages, objects and classes, innermost first. This phase runs before the namer, so there are no symbols
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

    // adds the class's scaladoc as a `ScaladocCarrier` annotation (and as an attachment, for macros of the same run)
    private def embed(cls: ClassDef): ClassDef = {
      val embedded = for {
        str <- SourceCodeUtils.extractComment(cls.pos)
        doc <- Scaladoc.fromString(str, strict = true) match {
                 case Left(err) =>
                   reportError(cls.pos, err.getMessage, cls.symbol)
                   None
                 case Right(doc) =>
                   Some(doc)
               }
      } yield {
        val Modifiers(flags, privateWithin, annos) = cls.mods
        val carrier = Annotation(
          carrierTpe,
          Nil,
          ListMap(
            TermName("text") -> LiteralAnnotArg(Constant(str)),
            TermName("tags") -> LiteralAnnotArg(Constant(true)))).tree

        cls.copy(mods = Modifiers(flags, privateWithin, annos :+ carrier)).updateAttachment(doc).setPos(cls.pos)
      }

      embedded getOrElse cls
    }

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
        case cls: ClassDef =>
          // nested classes first, with this class as their owner; they are visited even if this class is not embedded
          val body = withOwner(cls.name.decoded)(transformTemplate(cls.impl))
          val res  = treeCopy.ClassDef(cls, cls.mods, cls.name, cls.tparams, body)
          if (filter.accepts(path, fullName(cls.name))) embed(res) else res
        case _ =>
          super.transform(tree)
      }
    }
  }
}