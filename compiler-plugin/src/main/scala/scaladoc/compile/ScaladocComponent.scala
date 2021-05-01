package scaladoc.compile

import scaladoc.Scaladoc
import scaladoc.annotation.ScaladocCarrier
import scaladoc.utils.SourceCodeUtils

import scala.tools.nsc
import nsc.Global
import nsc.plugins.{Plugin, PluginComponent}
import nsc.ast.TreeDSL
import nsc.transform.{Transform, TypingTransformers}
import scala.collection.immutable.ListMap


class ScaladocComponent(
  plugin: Plugin,
  val global: Global) extends PluginComponent with Transform with TypingTransformers with TreeDSL with CrossVersionSupport  {
  import global._

  override val runsAfter  = "parser" :: Nil
  override val runsBefore = "namer" :: Nil
  override val phaseName  = plugin.name

  override def newTransformer(unit: CompilationUnit): Trans = new Trans(unit)

  class Trans(unit: CompilationUnit) extends TypingTransformer(unit) {
    private val carrierTpe = typeOf[ScaladocCarrier]

//    inform("COOKED DOC COMMENTS " + show(global.cookedDocComments))

    override def transform(tree: global.Tree): global.Tree = {
      tree match {
//        case doc @ DocDef(comment, definition) =>
//          inform(s"DocDef [${comment.raw}] for ${show(definition)}")
//          doc
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