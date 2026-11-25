package scaladoc.macros

import scaladoc.*
import scaladoc.parser.{Defines, ScaladocParser}

import scala.quoted.*

object ScaladocMacro {

  /** The scaladoc of `T`: its doc comment, from `Symbol.docstring`, in Markdown, Scala 3's default, unless a given
    * `CommentSyntax` in scope says otherwise (`import scaladoc.syntax.wiki.given`) or the comment does (`@syntax wiki`).
    * A class that has no doc comment there, like one from a Scala 2 library, may have its scaladoc embedded by the
    * compiler plugin, in a `ScaladocCarrier` annotation: that's in wiki syntax, Scala 2's only one.
    */
  def scaladoc[T: Type](using q: Quotes): Expr[Option[Scaladoc]] = {
    import q.reflect.*

    val sym = TypeRepr.of[T].typeSymbol

    def parsed(raw: String, result: Either[ScaladocException, Scaladoc]): Expr[Option[Scaladoc]] = result match {
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

    docstring(sym) match {
      case Some(raw) =>
        parsed(raw, ScaladocParser.docstringWithin(raw, strict = true, syntaxInScope, outerDefines(sym)))
      case None =>
        carrier(sym) match {
          case Some((text, tags)) =>
            parsed(text, ScaladocParser.within(text, tags, strict = true, CommentSyntax.Wiki, outerDefines(sym)))
          case None => '{ None }
        }
    }
  }

  // The defines a comment uses besides its own, where Scala 3's scaladoc looks them up (`CommentExpander.lookupVariable`):
  // the class's self type and base classes, in linearization order, then its enclosing classes and objects, searched
  // the same way, outward. The first one found wins.
  private def outerDefines(using q: Quotes)(sym: q.reflect.Symbol): Map[String, String] = {
    import q.reflect.*
    // the classes of the declared self type, from the class's tree, which a class from another module or a jar may not
    // have: then it has no self type here. The tree's self type is the class's own type and the declared one, `A & C`
    def selfType(site: Symbol): List[Symbol] = {
      def parts(t: TypeRepr): List[TypeRepr] = t match {
        case AndType(a, b) => parts(a) ++ parts(b)
        case other         => List(other)
      }
      scala.util.Try(site.tree).toOption match {
        case Some(cls: ClassDef) =>
          cls.self.toList.flatMap(self => parts(self.tpt.tpe)).map(_.typeSymbol).filterNot(s => s.isNoSymbol || s == site)
        case _ => Nil
      }
    }
    def searchList(site: Symbol): List[Symbol] =
      if (site.isClassDef) selfType(site) ++ site.typeRef.baseClasses else Nil
    val sites   = Iterator.iterate(sym)(_.owner).takeWhile(s => !s.isNoSymbol && !s.isPackageDef).toList
    val outer   = sites.flatMap(searchList).distinct.filterNot(_ == sym)
    val defines = outer.map { s =>
      docstring(s) match {
        case Some(raw) => ScaladocParser.defines(raw, hasTags = true)
        case None      => carrier(s).fold(Map.empty[String, String]) { case (text, tags) => ScaladocParser.defines(text, tags) }
      }
    }
    Defines.inOrder(defines)
  }

  // The symbol's doc comment. With `-Xread-docs`, a symbol from TASTy gets its comment when its TASTy is read, and an
  // object's class can be there before that: `T.type` doesn't read it, so `docstring` would be `None`. Its declarations
  // do, as any use of the object would.
  private def docstring(using q: Quotes)(sym: q.reflect.Symbol): Option[String] = {
    if (sym.isClassDef) sym.declarations
    sym.docstring
  }

  // the syntax a given in scope says, `import scaladoc.syntax.wiki.given`: its type is the syntax
  private def syntaxInScope(using q: Quotes): CommentSyntax = {
    import q.reflect.*
    Implicits.search(TypeRepr.of[CommentSyntax]) match {
      case found: ImplicitSearchSuccess =>
        val tpe = found.tree.tpe.widenTermRefByName
        if (tpe <:< TypeRepr.of[CommentSyntax.Wiki.type]) CommentSyntax.Wiki
        else if (tpe <:< TypeRepr.of[CommentSyntax.Markdown.type]) CommentSyntax.Markdown
        else {
          report.warning(
            "Scaladoc.of reads the syntax from the given's type: use scaladoc.syntax.wiki or scaladoc.syntax.markdown; " +
              "Markdown it is")
          CommentSyntax.Markdown
        }
      case _ => CommentSyntax.Markdown
    }
  }

  // the text of a `ScaladocCarrier` annotation, which the compiler plugin puts on Scala 2 classes, and whether it has
  // the comment's markers; by the annotation's name, so that it needn't be on the class path
  private def carrier(using q: Quotes)(sym: q.reflect.Symbol): Option[(String, Boolean)] = {
    import q.reflect.*
    sym.annotations.collectFirst {
      case annotation @ Apply(_, args) if annotation.tpe.typeSymbol.fullName == "scaladoc.annotation.ScaladocCarrier" =>
        val values = args.zipWithIndex.map {
          case (NamedArg(name, Literal(c)), _) => name -> c.value
          case (Literal(c), 0)                 => "text" -> c.value
          case (Literal(c), 1)                 => "tags" -> c.value
          case (other, i)                      => s"$i" -> other
        }.toMap
        (values.get("text"), values.get("tags")) match {
          case (Some(text: String), Some(tags: Boolean)) => Some((text, tags))
          case _                                         => None
        }
    }.flatten
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
      case Strikethrough(v) => '{ Markup.Strikethrough(${ spansExpr(v) }) }
      case Image(src, alt)  => '{ Markup.Image(${ Expr(src) }, ${ Expr(alt) }) }
      case LineBreak        => '{ Markup.LineBreak }
      case Checkbox(v)      => '{ Markup.Checkbox(${ Expr(v) }) }
      case FootnoteReference(v) => '{ Markup.FootnoteReference(${ Expr(v) }) }
      case HtmlTag(v)     => '{ Markup.HtmlTag(${ Expr(v) }) }
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

  private def rowExpr(x: Markup.Table.Row)(using Quotes): Expr[Markup.Table.Row] = {
    val cells = x.cells.map(cell => '{ Markup.Paragraph(${ spansExpr(cell.markup) }) })
    '{ Markup.Table.Row(${ Expr.ofList(cells) }) }
  }

  private def alignmentExpr(x: Markup.Table.Alignment)(using Quotes): Expr[Markup.Table.Alignment] = x match {
    case Markup.Table.Alignment.Left   => '{ Markup.Table.Alignment.Left }
    case Markup.Table.Alignment.Center => '{ Markup.Table.Alignment.Center }
    case Markup.Table.Alignment.Right  => '{ Markup.Table.Alignment.Right }
  }

  private def markupExpr(x: Markup)(using Quotes): Expr[Markup] = {
    import Markup.*
    x match {
      case x: Span           => spanExpr(x)
      case CodeBlock(v, l)   => '{ Markup.CodeBlock(${ Expr(v) }, ${ Expr(l) }) }
      case BlockQuote(c)     => '{ Markup.BlockQuote(${ documentExpr(c) }) }
      case Footnote(label, c) => '{ Markup.Footnote(${ Expr(label) }, ${ documentExpr(c) }) }
      case UnorderedList(items)      => '{ Markup.UnorderedList(${ Expr.ofList(items.map(documentExpr)) }) }
      case OrderedList(items, style) => '{ Markup.OrderedList(${ Expr.ofList(items.map(documentExpr)) }, ${ styleExpr(style) }) }
      case HorizontalRule    => '{ Markup.HorizontalRule }
      case Table(header, alignments, rows) =>
        '{ Markup.Table(${ rowExpr(header) }, ${ Expr.ofList(alignments.map(alignmentExpr)) }, ${ Expr.ofList(rows.map(rowExpr)) }) }
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
