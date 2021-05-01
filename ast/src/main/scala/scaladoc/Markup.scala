package scaladoc

sealed trait Markup extends Product with Serializable {
  def isBlank: Boolean
  def nonBlank: Boolean = !isBlank
  def plainString: String
}

object Markup {

  sealed trait Span extends Markup

  sealed trait HasValue { this: Markup =>
    def value: String
    override def isBlank: Boolean = value.forall(_.isWhitespace)
    override def plainString: String = value
  }

  final case class PlainText(value: String) extends Span with HasValue

  final case class Monospace(value: String) extends Span with HasValue

  final case class Italic(value: String) extends Span with HasValue

  final case class Bold(value: String) extends Span with HasValue

  final case class Underline(value: String) extends Span with HasValue

  final case class Superscript(value: String) extends Span with HasValue

  final case class Subscript(value: String) extends Span with HasValue

  final case class Link(value: String) extends Span with HasValue

  final case class CodeBlock(value: String) extends Markup with HasValue

  final case class Paragraph(markup: List[Span]) extends Markup {
    override def isBlank: Boolean = markup.forall(_.isBlank)
    override def plainString: String = markup.map(_.plainString).mkString("\n")
  }

  final object Paragraph {
    def apply(x: Span, xs: Span*): Paragraph = Paragraph(x +: xs.toList)
  }

  case class Document(elements: List[Markup]) extends Markup {
    override def isBlank: Boolean = elements.forall(_.isBlank)
    override def plainString: String = elements.map(_.plainString).mkString("\n")
  }
  object Document {
    def apply(x: Markup, xs: Markup*): Document = Document(x +: xs.toList)
  }

  final case class Heading(level: Heading.Level, text: String) extends Markup {
    override def isBlank: Boolean = text.isEmpty
    override def plainString: String = text
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