package scaladoc

trait Trimmed

sealed trait Markup extends Product with Serializable {
  type Self <: Markup
  def isBlank: Boolean
  def nonBlank: Boolean = !isBlank
  def plainString: String
  def trimmed: Self with Trimmed
}

object Markup {

  private[scaladoc] def trimML(x: String): String = x.linesIterator.map(_.trim).filterNot(_.isEmpty).mkString("\n")

  sealed trait Span extends Markup {
    override type Self <: Span
  }

  sealed trait HasValue { this: Markup =>
    def value: String
    override def isBlank: Boolean = value.forall(_.isWhitespace)
  }

  case class PlainText(value: String) extends Span with HasValue {
    type Self = PlainText
    override def plainString: String = value
    def trimmed: Self with Trimmed = new PlainText(trimML(value)) with Trimmed
  }

  case class Monospace(value: String) extends Span with HasValue {
    type Self = Monospace
    override def plainString: String = s"`$value`"
    def trimmed: Self with Trimmed = new Monospace(trimML(value)) with Trimmed

  }

  case class Italic(value: String) extends Span with HasValue {
    type Self = Italic
    override def plainString: String = s"''$value''"
    def trimmed: Self with Trimmed = new Italic(trimML(value)) with Trimmed

  }

  case class Bold(value: String) extends Span with HasValue {
    type Self = Bold
    override def plainString: String = s"'''$value'''"
    def trimmed: Self with Trimmed = new Bold(trimML(value)) with Trimmed

  }

  case class Underline(value: String) extends Span with HasValue {
    type Self = Underline
    override def plainString: String = s"__${value}__"
    def trimmed: Self with Trimmed = new Underline(trimML(value)) with Trimmed
  }

  case class Superscript(value: String) extends Span with HasValue {
    type Self = Superscript
    override def plainString: String = s"`^$value^"
    def trimmed: Self with Trimmed = new Superscript(trimML(value)) with Trimmed

  }

  case class Subscript(value: String) extends Span with HasValue {
    type Self = Superscript
    override def plainString: String = s",,$value,,"
    def trimmed: Self with Trimmed = new Superscript(trimML(value)) with Trimmed
  }

  case class Link(value: String) extends Span with HasValue {
    type Self = Link
    override def plainString: String = s"[[$value]]"
    def trimmed: Self with Trimmed = new Link(trimML(value)) with Trimmed
  }

  case class CodeBlock(value: String) extends Markup with HasValue  {
    override type Self = CodeBlock
    override def plainString: String = s"{{{$value}}}"
    def trimmed: Self with Trimmed = new CodeBlock(trimML(value)) with Trimmed

  }

  case class Paragraph(markup: List[Span]) extends Markup {
    type Self = Paragraph
    override def isBlank: Boolean = markup.forall(_.isBlank)
    override def plainString: String = {
      val sb = new StringBuilder
      markup.zipWithIndex foreach {
        case (m: Span with HasValue, _) => sb.append(m.plainString)
        case (m, i) =>
          sb.append(m.plainString)
          if (i < markup.size) sb.append("\n")
      }
      sb.toString
    }
    def trimmed: Self with Trimmed = {
      def trim(markup: List[Span]): List[Span] = markup match {
        case Nil => Nil
        case x :: Nil => List(x.trimmed)
        case x :: xs => List(x.trimmed, PlainText(" ")) ++ trim(xs)
      }
      new Paragraph(trim(markup)) with Trimmed
    }
  }

  final object Paragraph {
    def apply(x: Span, xs: Span*): Paragraph = Paragraph(x +: xs.toList)
  }

  case class Document(elements: List[Markup]) extends Markup {
    override type Self = Document
    override def isBlank: Boolean = elements.forall(_.isBlank)
    override def plainString: String = {
      val sb = new StringBuilder
      elements foreach {
        case x: Span =>
          sb.append(x.plainString)
        case x =>
          if(sb.nonEmpty) sb.append("\n")
          sb.append(x.plainString)
      }
      sb.toString
    }
    def trimmed: Self with Trimmed = {
      def trim(prev: Option[Markup], rest: List[Markup]): List[Markup] = (prev, rest) match {
        case (_, Nil)                          => Nil
        case (Some(_: Span), (x: Span) :: Nil) => List(PlainText(" "), x.trimmed)
        case (Some(_: Span), x :: Nil)         => List(x.trimmed)
        case (Some(_), x :: Nil)               => List(x.trimmed)
        case (None, x :: Nil)                  => List(x.trimmed)
        case (_, x :: xs)                      => List(x.trimmed) ++ trim(Some(x), xs)
      }
      new Document(trim(None, elements)) with Trimmed
    }
  }
  object Document {
    def apply(x: Markup, xs: Markup*): Document = Document(x +: xs.toList)
  }

  case class Heading(level: Heading.Level, text: String) extends Markup {
    type Self = Heading
    override def isBlank: Boolean = text.isEmpty
    override def plainString: String = text
    def trimmed: Self with Trimmed = new Heading(level, text.trim) with Trimmed
  }

  final object Heading {

    sealed trait Level extends Product { def value: Int }

    final case object One extends Level { def value = 1 }

    final case object Two extends Level { def value = 2 }

    final case object Three extends Level { def value = 3 }

    final case object Four extends Level { def value = 4 }

    final case object Five extends Level { def value = 5 }

    final case object Six extends Level { def value = 6 }

    def apply(l: Int, text: String): Heading = l match {
      case 1 => Heading(One, text)
      case 2 => Heading(Two, text)
      case 3 => Heading(Three, text)
      case 4 => Heading(Four, text)
      case 5 => Heading(Five, text)
      case 6 => Heading(Six, text)
      case _ => throw new IllegalArgumentException(s"Level $l is not supported")
    }
  }

  // TODO: list blocks
}