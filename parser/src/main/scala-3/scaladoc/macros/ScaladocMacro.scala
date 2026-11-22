package scaladoc.macros

import scaladoc.*
import scaladoc.parser.ScaladocParser

import scala.quoted.*

object ScaladocMacro {

  def scaladoc[T: Type](using q: Quotes): Expr[Option[Scaladoc]] = {
    import q.reflect.*

    val sym = TypeRepr.of[T].typeSymbol
    sym.docstring match {
      case None => '{ None }
      case Some(raw) =>
        ScaladocParser.fromDocstring(raw, strict = true) match {
          case Right(doc) => '{ Some(${ Expr(doc) }) }
          case Left(err) =>
            val pos = sym.pos.getOrElse(Position.ofMacroExpansion)
            report.errorAndAbort(
              s"""Error extracting scaladoc from comment:
                 |```
                 |$raw
                 |```
                 |Reason: ${err.getMessage}
                 |""".stripMargin,
              pos
            )
        }
    }
  }

  given ToExpr[Scaladoc] with {
    def apply(x: Scaladoc)(using Quotes): Expr[Scaladoc] = '{ Scaladoc(${ Expr.ofList(x.tags.map(tagExpr)) }) }
  }

  private def levelExpr(x: Markup.Heading.Level)(using Quotes): Expr[Markup.Heading.Level] = {
    import Markup.Heading.*
    x match {
      case One   => '{ Markup.Heading.One }
      case Two   => '{ Markup.Heading.Two }
      case Three => '{ Markup.Heading.Three }
      case Four  => '{ Markup.Heading.Four }
      case Five  => '{ Markup.Heading.Five }
      case Six   => '{ Markup.Heading.Six }
    }
  }

  private def spanExpr(x: Markup.Span)(using Quotes): Expr[Markup.Span] = {
    import Markup.*
    x match {
      case PlainText(v)   => '{ Markup.PlainText(${ Expr(v) }) }
      case Monospace(v)   => '{ Markup.Monospace(${ Expr(v) }) }
      case Italic(v)      => '{ Markup.Italic(${ spansExpr(v) }) }
      case Bold(v)        => '{ Markup.Bold(${ spansExpr(v) }) }
      case Underline(v)   => '{ Markup.Underline(${ spansExpr(v) }) }
      case Superscript(v) => '{ Markup.Superscript(${ spansExpr(v) }) }
      case Subscript(v)   => '{ Markup.Subscript(${ spansExpr(v) }) }
      case Link(t, title) => '{ Markup.Link(${ Expr(t) }, ${ spansExpr(title) }) }
    }
  }

  private def spansExpr(xs: List[Markup.Span])(using Quotes): Expr[List[Markup.Span]] = Expr.ofList(xs.map(spanExpr))

  private def documentExpr(x: Markup.Document)(using Quotes): Expr[Markup.Document] =
    '{ Markup.Document(${ Expr.ofList(x.elements.map(markupExpr)) }) }

  private def styleExpr(x: Markup.OrderedList.Style)(using Quotes): Expr[Markup.OrderedList.Style] = {
    import Markup.OrderedList.*
    x match {
      case Decimal    => '{ Markup.OrderedList.Decimal }
      case LowerRoman => '{ Markup.OrderedList.LowerRoman }
      case UpperRoman => '{ Markup.OrderedList.UpperRoman }
      case LowerAlpha => '{ Markup.OrderedList.LowerAlpha }
      case UpperAlpha => '{ Markup.OrderedList.UpperAlpha }
    }
  }

  private def markupExpr(x: Markup)(using Quotes): Expr[Markup] = {
    import Markup.*
    x match {
      case x: Span           => spanExpr(x)
      case CodeBlock(v)      => '{ Markup.CodeBlock(${ Expr(v) }) }
      case UnorderedList(items)      => '{ Markup.UnorderedList(${ Expr.ofList(items.map(documentExpr)) }) }
      case OrderedList(items, style) => '{ Markup.OrderedList(${ Expr.ofList(items.map(documentExpr)) }, ${ styleExpr(style) }) }
      case Heading(l, v)     => '{ Markup.Heading(${ levelExpr(l) }, ${ spansExpr(v) }) }
      case Paragraph(spans)  => '{ Markup.Paragraph(${ Expr.ofList(spans.map(spanExpr)) }) }
      case Document(elems)   => '{ Markup.Document(${ Expr.ofList(elems.map(markupExpr)) }) }
    }
  }

  private def tagExpr(x: Tag)(using Quotes): Expr[Tag] = {
    import Tag.*
    def m(x: Markup) = markupExpr(x)
    def s(x: String) = Expr(x)
    x match {
      case InheritDoc                 => '{ Tag.InheritDoc }
      case Documentable               => '{ Tag.Documentable }
      case TypeParam(name, x)         => '{ Tag.TypeParam(${ s(name) }, ${ m(x) }) }
      case Param(name, x)             => '{ Tag.Param(${ s(name) }, ${ m(x) }) }
      case Constructor(x)             => '{ Tag.Constructor(${ m(x) }) }
      case Returns(x)                 => '{ Tag.Returns(${ m(x) }) }
      case Throws(err, x)             => '{ Tag.Throws(${ s(err) }, ${ m(x) }) }
      case Note(x)                    => '{ Tag.Note(${ m(x) }) }
      case Example(x)                 => '{ Tag.Example(${ m(x) }) }
      case UseCase(x)                 => '{ Tag.UseCase(${ m(x) }) }
      case See(x)                     => '{ Tag.See(${ m(x) }) }
      case Author(text)               => '{ Tag.Author(${ s(text) }) }
      case Version(text)              => '{ Tag.Version(${ s(text) }) }
      case Since(text)                => '{ Tag.Since(${ s(text) }) }
      case Todo(x)                    => '{ Tag.Todo(${ m(x) }) }
      case Deprecated(x)              => '{ Tag.Deprecated(${ m(x) }) }
      case Migration(x)               => '{ Tag.Migration(${ m(x) }) }
      case Group(id)                  => '{ Tag.Group(${ s(id) }) }
      case GroupName(id, value)       => '{ Tag.GroupName(${ s(id) }, ${ s(value) }) }
      case GroupDescription(id, x)    => '{ Tag.GroupDescription(${ s(id) }, ${ m(x) }) }
      case GroupPriority(id, value)   => '{ Tag.GroupPriority(${ s(id) }, ${ Expr(value) }) }
      case OtherTag(label, x)         => '{ Tag.OtherTag(${ s(label) }, ${ m(x) }) }
      case Description(x)             => '{ Tag.Description(${ m(x) }) }
    }
  }
}
