package scaladoc.macros

import scaladoc.{CommentSyntax, Scaladoc}
import scaladoc.annotation.ScaladocCarrier
import scaladoc.parser.{Defines, ScaladocParser}
import scaladoc.utils.SourceCodeUtils

import scala.reflect.macros.blackbox

trait ExtractScaladoc extends AnnotationSupport {
  val c: blackbox.Context
  import c.universe._

  private val carrierTpe = typeOf[ScaladocCarrier]

  def fromSourceCode(pos: Position, outer: => Map[String, String]): Option[Scaladoc] = {
    SourceCodeUtils.extractComment(pos) map { str =>
      ScaladocParser.within(str, hasTags = true, strict = true, CommentSyntax.Wiki, outer) match {
        case Right(doc) =>
          doc
        case Left(err) =>
          c.abort(
            pos,
            s"""Error extracting scaladoc from comment:
             |```
             |$str
             |```
             |Reason: ${err.getMessage}
             |""".stripMargin
          )
      }
    }
  }

  def fromAttachment: Option[Scaladoc] = c.internal.attachments(c.prefix.tree).get[Scaladoc]

  def fromAnnotatedType(t: Type, outer: => Map[String, String]): Option[Scaladoc] = {
    carrier(t.typeSymbol) map { case (body, hasTags) =>
      ScaladocParser.within(body, hasTags, strict = true, CommentSyntax.Wiki, outer) match {
        case Right(doc) => doc
        case Left(err)  => c.abort(t.typeSymbol.pos, err.getMessage)
      }
    }
  }

  // the text of the class's `ScaladocCarrier`, which the compiler plugin adds, and whether it has the comment's markers
  private def carrier(sym: Symbol): Option[(String, Boolean)] = {
    sym.info // a class from a jar has its annotations once it's loaded
    sym.annotations collectFirst { case a if a.tree.tpe =:= carrierTpe => a.args }
  }

  /** The defines a comment uses besides its own, where Scala 2's scaladoc looks them up (`DocComments.lookupVariable`):
    * the class's base classes, of its self type if it has one, in linearization order; for an object, the object and
    * then its companion class's; then its enclosing classes and objects, searched the same way, outward. The first one
    * found wins. A class's comment is its source when it's compiled in this run, or else its `ScaladocCarrier`.
    */
  def outerDefines(sym: Symbol): Map[String, String] = {
    def bases(k: Symbol): List[Symbol] = if (k.isClass) k.asClass.selfType.baseClasses else Nil
    def searchList(site: Symbol): List[Symbol] =
      if (site.isModuleClass) site :: bases(site) ++ bases(site.asClass.module.companion) else bases(site)
    def isTop(k: Symbol)  = k == NoSymbol || k.isPackage || k.isPackageClass
    def comment(k: Symbol): Option[(String, Boolean)] = {
      val pos = if (k.isModuleClass) k.asClass.module.pos else k.pos
      SourceCodeUtils.extractComment(pos).map(_ -> true) orElse carrier(k)
    }
    val sites = Iterator.iterate(sym)(_.owner).takeWhile(k => !isTop(k)).toList
    val outer = sites.flatMap(searchList).distinct.filterNot(_ == sym)
    Defines.inOrder(outer.map(k => comment(k).fold(Map.empty[String, String]) { case (text, tags) => ScaladocParser.defines(text, tags) }))
  }
}
