package scaladoc.macros

import scaladoc._
import scaladoc.annotation.ScaladocCarrier

import scala.reflect.macros._


class ScaladocMacro(val c: blackbox.Context) extends ExtractScaladoc with AnnotationSupport {
  import c.universe._

  val carrierTpe = typeOf[ScaladocCarrier]
  val prefix     = q"_root_.scaladoc"

  def scaladoc[T](implicit t: WeakTypeTag[T]): c.Expr[Option[Scaladoc]] =
    c.Expr[Option[Scaladoc]](optionTree(docOf(t.tpe.typeSymbol)))

  def typedoc[T](implicit t: WeakTypeTag[T]): c.Expr[Typedoc] = c.Expr[Typedoc](typedocTree(t.tpe.typeSymbol))

  private def optionTree(doc: Option[Scaladoc]): Tree = doc match {
    case Some(doc) => q"scala.Some(${materialize(doc)})"
    case None      => q"scala.None"
  }

  // the node of a class or object, with its variants or fields
  private def typedocTree(sym: Symbol): Tree = {
    sym.info // a class from a jar has its children and constructor once it's loaded
    val name = sym.name.decodedName.toString
    val doc  = docOf(sym)
    val cls  = if (sym.isClass) Some(sym.asClass) else None
    cls match {
      case Some(cls) if cls.isSealed =>
        val variants = inDeclarationOrder(cls.knownDirectSubclasses.toList) map typedocTree
        q"$prefix.Typedoc.Sum($name, ${optionTree(doc)}, scala.List(..$variants))"
      case Some(cls) if cls.isModuleClass =>
        q"$prefix.Typedoc.Singleton($name, ${optionTree(doc)})"
      case Some(cls) if cls.isCaseClass =>
        val params = cls.primaryConstructor.asMethod.paramLists.headOption.getOrElse(Nil)
        val fields = params map { p =>
          val field = p.name.decodedName.toString
          val fieldDoc = doc.flatMap(_.params.get(field)) match {
            case Some(m) => q"scala.Some(${markupTree(m)})"
            case None    => q"scala.None"
          }
          q"$prefix.Typedoc.Field($field, $fieldDoc)"
        }
        q"$prefix.Typedoc.Product($name, ${optionTree(doc)}, scala.List(..$fields))"
      case _ =>
        q"$prefix.Typedoc.Plain($name, ${optionTree(doc)})"
    }
  }

  // `knownDirectSubclasses` is a set: in the order of the source when the hierarchy is compiled in this run, and by
  // name otherwise, as classes from a jar have no positions
  private def inDeclarationOrder(children: List[Symbol]): List[Symbol] = {
    val positions = children.map(declarationPos)
    if (positions.forall(_ != NoPosition))
      children.zip(positions).sortBy { case (_, pos) => (pos.source.path, pos.point) }.map(_._1)
    else children.sortBy(s => (s.name.decodedName.toString, s.fullName))
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
        case Link(target, title)                => q"$prefix.Markup.Link($target, ${spans(title)})"
        case HtmlTag(data)                      => q"$prefix.Markup.HtmlTag($data)"
        case CodeBlock(text, language)          => q"$prefix.Markup.CodeBlock($text, $language)"
        case BlockQuote(content)                => q"$prefix.Markup.BlockQuote(${markupTree(content)})"
        case Footnote(label, content)           => q"$prefix.Markup.Footnote($label, ${markupTree(content)})"
        case Strikethrough(els)                 => q"$prefix.Markup.Strikethrough(${spans(els)})"
        case Image(src, alt)                    => q"$prefix.Markup.Image($src, $alt)"
        case LineBreak                          => q"$prefix.Markup.LineBreak"
        case Checkbox(checked)                  => q"$prefix.Markup.Checkbox($checked)"
        case FootnoteReference(label)           => q"$prefix.Markup.FootnoteReference($label)"
        case UnorderedList(items)               => q"$prefix.Markup.UnorderedList(scala.List(..${trees(items)}))"
        case OrderedList(items, style)          =>
          q"$prefix.Markup.OrderedList(scala.List(..${trees(items)}), $prefix.Markup.OrderedList.${TermName(style.toString)})"
        case HorizontalRule                     => q"$prefix.Markup.HorizontalRule"
        case Table(header, alignments, rows)    =>
          def row(r: Table.Row): Tree  = q"$prefix.Markup.Table.Row(scala.List(..${trees(r.cells)}))"
          val aligned                  = alignments.map(a => q"$prefix.Markup.Table.Alignment.${TermName(a.toString)}")
          q"$prefix.Markup.Table(${row(header)}, scala.List(..$aligned), scala.List(..${rows.map(row)}))"
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
