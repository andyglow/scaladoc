import scaladoc.macros.ScaladocMacro
import scaladoc.parser.*
import scaladoc.utils.{DumpAst, IdentWriter}

package object scaladoc {

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

    /** Extracts the scaladoc of `T` at compile time from `Symbol.docstring`.
      *
      * The compiler only keeps doc comments of sources compiled in the same run, unless the calling module is
      * compiled with `-Yread-docs`, which also reads them from TASTy (other modules, jars, files not recompiled
      * by an incremental compile).
      */
    inline def of[T]: Option[Scaladoc] = ${ ScaladocMacro.scaladoc[T] }
  }
}
