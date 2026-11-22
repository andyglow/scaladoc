package scaladoc.macros

import scaladoc._
import scaladoc.annotation.ScaladocCarrier

import scala.reflect.macros._


class ScaladocMacro(val c: blackbox.Context) extends ExtractScaladoc with AnnotationSupport {
  import c.universe._

  val carrierTpe = typeOf[ScaladocCarrier]
  val prefix     = q"_root_.scaladoc"

  def scaladoc[T](implicit t: WeakTypeTag[T]): c.Expr[Option[Scaladoc]] = {
    val scd = fromAttachment orElse fromAnnotatedType(t.tpe) orElse fromSourceCode(t.tpe.typeSymbol.pos)

//    c.info(c.enclosingPosition, s"${show(t.tpe)} -> ${show(scd)}", force = true)

    scd match {
      case Some(doc) => c.Expr[Option[Scaladoc]](q"scala.Some(${materialize(doc)})")
      case None      => c.Expr[Option[Scaladoc]](q"scala.None")
    }
  }

  // plain functions rather than the extractors in patterns: keeps the pattern matches checkable for exhaustivity
  private def tagTree(x: Tag): Tree               = TagTree.unapply(x).get
  private def markupTree(x: Markup): Tree         = MarkupTree.unapply(x).get
  private def levelTree(x: Markup.Heading.Level): Tree = HeadingLevelTree.unapply(x).get

  def materialize(x: Scaladoc): Tree = {
    val tags = x.tags map tagTree
    q"$prefix.Scaladoc(scala.List(..$tags))"
  }

  object MarkupTree {
    import Markup._

    def unapply(x: Markup): Option[Tree] = Some {
      def trees(ls: List[Markup]): List[Tree] = ls map markupTree
      def spans(ls: List[Span]): Tree        = q"scala.List(..${trees(ls)})"
      x match {
        case Document(els)                      => q"$prefix.Markup.Document(scala.List(..${trees(els)}))"
        case Paragraph(els)                     => q"$prefix.Markup.Paragraph(scala.List(..${trees(els)}))"
        case PlainText(text)                    => q"$prefix.Markup.PlainText($text)"
        case Monospace(text)                    => q"$prefix.Markup.Monospace($text)"
        case Italic(els)                        => q"$prefix.Markup.Italic(${spans(els)})"
        case Bold(els)                          => q"$prefix.Markup.Bold(${spans(els)})"
        case Underline(els)                     => q"$prefix.Markup.Underline(${spans(els)})"
        case Superscript(els)                   => q"$prefix.Markup.Superscript(${spans(els)})"
        case Subscript(els)                     => q"$prefix.Markup.Subscript(${spans(els)})"
        case Link(text)                         => q"$prefix.Markup.Link($text)"
        case CodeBlock(text)                    => q"$prefix.Markup.CodeBlock($text)"
        case Heading(l, els)                    => q"$prefix.Markup.Heading(${levelTree(l)}, ${spans(els)})"
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
        case InheritDoc               => q"$prefix.Tag.InheritDoc"
        case Documentable             => q"$prefix.Tag.Documentable"
        case TypeParam(name, m)       => q"$prefix.Tag.TypeParam($name, ${markupTree(m)})"
        case Param(name, m)           => q"$prefix.Tag.Param($name, ${markupTree(m)})"
        case Constructor(m)           => q"$prefix.Tag.Constructor(${markupTree(m)})"
        case Returns(m)               => q"$prefix.Tag.Returns(${markupTree(m)})"
        case Throws(err, m)           => q"$prefix.Tag.Throws($err, ${markupTree(m)})"
        case Note(m)                  => q"$prefix.Tag.Note(${markupTree(m)})"
        case Example(m)               => q"$prefix.Tag.Example(${markupTree(m)})"
        case UseCase(m)               => q"$prefix.Tag.UseCase(${markupTree(m)})"
        case See(link)                => q"$prefix.Tag.See(${markupTree(link)})"
        case Author(text)             => q"$prefix.Tag.Author($text)"
        case Version(text)            => q"$prefix.Tag.Version($text)"
        case Since(text)              => q"$prefix.Tag.Since($text)"
        case Todo(m)                  => q"$prefix.Tag.Todo(${markupTree(m)})"
        case Deprecated(m)            => q"$prefix.Tag.Deprecated(${markupTree(m)})"
        case Migration(m)             => q"$prefix.Tag.Migration(${markupTree(m)})"
        case Group(id)                => q"$prefix.Tag.Group($id)"
        case GroupName(id, value)     => q"$prefix.Tag.GroupName($id, $value)"
        case GroupDescription(id, m)  => q"$prefix.Tag.GroupDescription($id, ${markupTree(m)})"
        case GroupPriority(id, value) => q"$prefix.Tag.GroupPriority($id, $value)"
        case OtherTag(label, m)       => q"$prefix.Tag.OtherTag($label, ${markupTree(m)})"
        case Description(m)           => q"$prefix.Tag.Description(${markupTree(m)})"
      }
    }
  }
}
