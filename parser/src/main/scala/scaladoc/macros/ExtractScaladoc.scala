package scaladoc.macros

import scaladoc.annotation.ScaladocCarrier
import scaladoc.utils.SourceCodeUtils
import scaladoc.{Scaladoc, ScaladocException}

import scala.reflect.internal.util.NoSourceFile
import scala.reflect.macros.blackbox


trait ExtractScaladoc extends AnnotationSupport {
  val c: blackbox.Context
  import c.universe._

  private val carrierTpe = typeOf[ScaladocCarrier]

  def fromPosition(pos: Position): Option[Scaladoc] = {
    SourceCodeUtils.extractComment(pos) map { str =>
      Scaladoc.fromString(str, strict = true) match {
        case Right(doc) => doc
        case Left(err) => c.abort(pos, err.getMessage)
      }
    }
  }



  def fromContext: Option[Scaladoc] = c.internal.attachments(c.prefix.tree).get[Scaladoc]

  def fromType(t: Type): Option[Scaladoc] =  {
      // from java-annotation
      //      println("~~~~\n"+showRaw(t.tpe.typeSymbol.annotations+"\n~~~~"))
      t.typeSymbol.annotations collectFirst {
        case a if a.tree.tpe =:= carrierTpe =>

          val (body, hasTags) = a.args

          //          println("has annotation")
          //          val body: AtomicReference[String] = new AtomicReference[String]()
          //          val hasTags: AtomicBoolean = new AtomicBoolean()
          //          a.tree.children foreach {
          //            case NamedArg(Ident(TermName("text")), Literal(Constant(v: String))) => body.set(v)
          //            case NamedArg(Ident(TermName("tags")), Literal(Constant(v: Boolean))) => hasTags.set(v)
          //            case _ =>
          //          }
          //          val body = a.tree.children
          //          val hasTags = args.get(TermName("tags")).get

          Scaladoc.fromString(body, hasTags, strict = true) match {
            case Right(doc) => doc
            case Left(err) => c.abort(t.typeSymbol.pos, err.getMessage)
          }
      }
    }
}
