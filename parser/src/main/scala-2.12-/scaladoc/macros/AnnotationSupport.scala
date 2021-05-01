package scaladoc.macros

import scala.reflect.macros.blackbox

trait AnnotationSupport {
  val c: blackbox.Context
  import c.universe._

  implicit class AnnotaionOps(private val a: Annotation) {

    def args: (String, Boolean) = {
      var body   : String  = null
      var hasTags: Boolean = true

      a.tree.children foreach {
        case AssignOrNamedArg(Ident(TermName("text")), Literal(Constant(v: String)))  => body    = v
        case AssignOrNamedArg(Ident(TermName("tags")), Literal(Constant(v: Boolean))) => hasTags = v
        case _ =>
      }

      if (body eq null)
        c.abort(c.enclosingPosition,
          s"""expected annotation: scaladocx.annotation.ScaladocCarrier(text: String, tags: Boolean)
             |but got            : ${show(a)}
             |
             |If this error happens, we probably got into situation where
             |some incompatible changes have been applied to the annotation and
             |we try to extract scaladoc from models that were compiled with
             |different version of annotation.
             |""".stripMargin)

      (body, hasTags)
    }
  }
}
