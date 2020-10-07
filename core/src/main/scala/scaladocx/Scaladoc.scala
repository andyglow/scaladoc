package scaladocx

import scaladocx.macros.ScaladocMacro
import scaladocx.parse.StripCommentTags.Comment
import scaladocx.parse.{ParseScaladocTags, StripCommentTags}
import scaladocx.utils.{DumpAst, IdentWriter}


case class Scaladoc(tags: List[Tag]) extends Serializable {

  def pseudoCode: String = {
    val w = IdentWriter()
    DumpAst(this, w)
    w.toString
  }
}

object Scaladoc {

  def apply(x: Tag, xs: Tag*): Scaladoc = Scaladoc(x +: xs.toList)

  def fromString(x: String, hasTags: Boolean = true, strict: Boolean = false): Either[ScaladocException, Scaladoc] = {

    def parse(x: Comment) = ParseScaladocTags.pure(x.body).right

    for {
      comment  <- { if (hasTags) StripCommentTags.pure(x, strict) else Right(Comment(Style.Stripped, x)) }.right
      scaladoc <- comment.style match {
        case Style.Stripped if !hasTags => parse(comment)
        case Style.Scaladoc             => parse(comment)
        case style: Style.NonScaladoc   => Left(NonScaladocStyle(style)).right
      }
    } yield scaladoc
  }

  def of[T]: Option[Scaladoc] = macro ScaladocMacro.scaladoc[T]
}