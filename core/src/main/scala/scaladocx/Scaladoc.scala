package scaladocx

import scaladocx.parse.{ParseScaladocTags, StripCommentTags}


case class Scaladoc(tags: List[Tag])

object Scaladoc {

  def fromString(x: String, strict: Boolean = false): Either[ScaladocException, Scaladoc] = for {
    comment  <- StripCommentTags.pure(x, strict).right
    scaladoc <- comment.style match {
      case Style.Scaladoc           => ParseScaladocTags.pure(comment.body).right
      case style: Style.NonScaladoc => Left(NonScaladocStyle(style)).right
    }
  } yield scaladoc
}