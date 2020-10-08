import scaladoc.macros.ScaladocMacro
import scaladoc.parser._
import scaladoc.utils.{DumpAst, IdentWriter}


package object scaladoc {
  import StripCommentTags._

  implicit class ScaladocOps(private val doc: Scaladoc) extends AnyVal {

    def pseudoCode: String = {
      val w = IdentWriter()
      DumpAst(doc, w)
      w.toString
    }
  }

  implicit class ScaladocCompanionOps(private val comp: Scaladoc.type) extends AnyVal {


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
}
