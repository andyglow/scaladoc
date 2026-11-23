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

    def fromString(x: String, hasTags: Boolean = true, strict: Boolean = false): Either[ScaladocException, Scaladoc] =
      ScaladocParser.fromString(x, hasTags, strict)

    /** Parses a raw doc comment as Scala 3's `Symbol.docstring` returns it. See `scaladoc.parser.ScaladocParser.fromDocstring`. */
    def fromDocstring(x: String, strict: Boolean = false): Either[ScaladocException, Scaladoc] =
      ScaladocParser.fromDocstring(x, strict)

    /** Extracts the scaladoc of `T` at compile time: from the compiler-plugin attachment, the `ScaladocCarrier`
      * annotation or the source code, in that order.
      */
    def of[T]: Option[Scaladoc] = macro ScaladocMacro.scaladoc[T]
  }
}
