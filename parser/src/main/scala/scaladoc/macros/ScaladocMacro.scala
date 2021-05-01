package scaladoc.macros

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import scaladoc._
import scaladoc.annotation.ScaladocCarrier

import scala.reflect.macros._


class ScaladocMacro(val c: blackbox.Context) extends ExtractScaladoc with AnnotationSupport {
  import c.universe._

  val carrierTpe = typeOf[ScaladocCarrier]
  val prefix     = q"_root_.scaladoc"

  def scaladoc[T](implicit t: WeakTypeTag[T]): c.Expr[Option[Scaladoc]] = {
    val scd = fromContext orElse fromType(t.tpe) orElse fromPosition(t.tpe.typeSymbol.pos)

//    c.info(c.enclosingPosition, s"${show(t.tpe)} -> ${show(scd)}", force = true)

    scd match {
      case Some(doc) => c.Expr[Option[Scaladoc]](q"scala.Some(${materialize(doc)})")
      case None      => c.Expr[Option[Scaladoc]](q"scala.None")
    }
  }

  def materialize(x: Scaladoc): Tree = {
    val tags = x.tags map { case TagTree(x) => x }
    q"$prefix.Scaladoc(scala.List(..$tags))"
  }

  object MarkupTree {
    import Markup._

    def unapply(x: Markup): Option[Tree] = Some {
      def trees(ls: List[Markup]): List[Tree] = ls flatMap MarkupTree.unapply
      x match {
        case Document(els)                      => q"$prefix.Markup.Document(scala.List(..${trees(els)}))"
        case Paragraph(els)                     => q"$prefix.Markup.Paragraph(scala.List(..${trees(els)}))"
        case PlainText(text)                    => q"$prefix.Markup.PlainText($text)"
        case Monospace(text)                    => q"$prefix.Markup.Monospace($text)"
        case Italic(text)                       => q"$prefix.Markup.Italic($text)"
        case Bold(text)                         => q"$prefix.Markup.Bold($text)"
        case Underline(text)                    => q"$prefix.Markup.Underline($text)"
        case Superscript(text)                  => q"$prefix.Markup.Superscript($text)"
        case Subscript(text)                    => q"$prefix.Markup.Subscript($text)"
        case Link(text)                         => q"$prefix.Markup.Link($text)"
        case CodeBlock(text)                    => q"$prefix.Markup.CodeBlock($text)"
        case Heading(HeadingLevelTree(l), text) => q"$prefix.Markup.Heading($l, $text)"
      }
    }
  }

  object HeadingLevelTree {
    import Markup._
    import Heading._

    def unapply(x: Level): Option[Tree] = Some {
      x match {
        case One   => q"$prefix.Markup.Heading.One"
        case Two   => q"$prefix.Markup.Heading.Two"
        case Three => q"$prefix.Markup.Heading.Three"
        case Four  => q"$prefix.Markup.Heading.Four"
        case Five  => q"$prefix.Markup.Heading.Five"
        case Six   => q"$prefix.Markup.Heading.Six"
      }
    }
  }

  object TagTree {
    import Tag._

    def unapply(x: Tag): Option[Tree] = Some {
      x match {
        case InheritDoc                          => q"$prefix.Tag.InheritDoc"
        case Documentable                        => q"$prefix.Tag.Documentable"
        case TypeParam(name, MarkupTree(m))      => q"$prefix.Tag.TypeParam($name, $m)"
        case Param(name, MarkupTree(m))          => q"$prefix.Tag.Param($name, $m)"
        case Constructor(MarkupTree(m))          => q"$prefix.Tag.Constructor($m)"
        case Returns(MarkupTree(m))              => q"$prefix.Tag.Returns($m)"
        case Throws(err, MarkupTree(m))          => q"$prefix.Tag.Throws($err, $m)"
        case Note(MarkupTree(m))                 => q"$prefix.Tag.Note($m)"
        case Example(MarkupTree(m))              => q"$prefix.Tag.Example($m)"
        case UseCase(MarkupTree(m))              => q"$prefix.Tag.UseCase($m)"
        case See(MarkupTree(link))               => q"$prefix.Tag.See($link)"
        case Author(text)                        => q"$prefix.Tag.Author($text)"
        case Version(text)                       => q"$prefix.Tag.Version($text)"
        case Since(text)                         => q"$prefix.Tag.Since($text)"
        case Todo(MarkupTree(m))                 => q"$prefix.Tag.Todo($m)"
        case Deprecated(MarkupTree(m))           => q"$prefix.Tag.Deprecated($m)"
        case Migration(MarkupTree(m))            => q"$prefix.Tag.Migration($m)"
        case Group(id)                           => q"$prefix.Tag.Group($id)"
        case GroupName(id, value)                => q"$prefix.Tag.GroupName($id, $value)"
        case GroupDescription(id, MarkupTree(m)) => q"$prefix.Tag.GroupDescription($id, $m)"
        case GroupPriority(id, value)            => q"$prefix.Tag.GroupPriority($id, $value)"
        case OtherTag(label, MarkupTree(m))      => q"$prefix.Tag.OtherTag($label, $m)"
        case Description(MarkupTree(m))          => q"$prefix.Tag.Description($m)"
      }
    }
  }
}
