package scaladoc.macros

import scaladoc.Scaladoc
import scaladoc.annotation.ScaladocCarrier
import scaladoc.utils.SourceCodeUtils

import scala.reflect.macros.blackbox

trait ExtractScaladoc extends AnnotationSupport {
  val c: blackbox.Context
  import c.universe._

  private val carrierTpe = typeOf[ScaladocCarrier]

  def fromSourceCode(pos: Position): Option[Scaladoc] = {
    SourceCodeUtils.extractComment(pos) map { str =>
      Scaladoc.fromString(str, strict = true) match {
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

  def fromAnnotatedType(t: Type): Option[Scaladoc] = {
    t.typeSymbol.annotations collectFirst {
      case a if a.tree.tpe =:= carrierTpe =>
        val (body, hasTags) = a.args

        Scaladoc.fromString(body, hasTags, strict = true) match {
          case Right(doc) => doc
          case Left(err)  => c.abort(t.typeSymbol.pos, err.getMessage)
        }
    }
  }
}
