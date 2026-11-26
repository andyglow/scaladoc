import scaladoc.macros.ScaladocMacro
import scaladoc.parser._
import scaladoc.utils.{DumpAst, IdentWriter}

package object scaladoc {
  import scala.language.experimental.macros

  implicit class ScaladocOps(private val doc: Scaladoc) extends AnyVal {

    def pseudoCode: String = {
      val w = IdentWriter()
      DumpAst(doc, w)
      w.toString
    }
  }

  implicit class ScaladocCompanionOps(private val comp: Scaladoc.type) extends AnyVal {

    /** Parses a comment, written in `syntax`. See `scaladoc.parser.ScaladocParser.fromString`. */
    def fromString(
      x: String,
      hasTags: Boolean = true,
      strict: Boolean = false,
      syntax: CommentSyntax = CommentSyntax.Wiki): Either[ScaladocException, Scaladoc] =
      ScaladocParser.fromString(x, hasTags, strict, syntax)

    /** Parses a raw doc comment as Scala 3's `Symbol.docstring` returns it, written in `syntax`. See
      * `scaladoc.parser.ScaladocParser.fromDocstring`.
      */
    def fromDocstring(
      x: String,
      strict: Boolean = false,
      syntax: CommentSyntax = CommentSyntax.Wiki): Either[ScaladocException, Scaladoc] =
      ScaladocParser.fromDocstring(x, strict, syntax)

    /** Extracts the scaladoc of `T` at compile time: from the compiler-plugin attachment, the `ScaladocCarrier`
      * annotation or the source code, in that order.
      */
    def of[T]: Option[Scaladoc] = macro ScaladocMacro.scaladoc[T]
  }

  implicit class TypedocCompanionOps(private val comp: Typedoc.type) extends AnyVal {

    /** Extracts the docs of `T` as a tree at compile time: its scaladoc, as `Scaladoc.of[T]` gives it, and those of
      * its variants if it's sealed, or of its fields if it's a case class. See `scaladoc.Typedoc`.
      */
    def of[T]: Typedoc = macro ScaladocMacro.typedoc[T]
  }
}
