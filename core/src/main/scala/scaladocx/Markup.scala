package scaladocx


sealed trait Markup
object Markup {
  sealed trait Span extends Markup
  final case object Empty extends Span
  final case class PlainText(value: String) extends Span
  final case class Monospace(value: Span) extends Span
  final case class Italic(value: Span) extends Span
  final case class Bold(value: Span) extends Span
  final case class Underline(value: Span) extends Span
  final case class Superscript(value: Span) extends Span
  final case class Subscript(value: Span) extends Span
  final case class Link(value: String) extends Span
  final case class CodeBlock(value: String) extends Markup
  final case class Paragraph(markup: List[Span])
  final case class Heading(level: Heading.Level, text: Markup) extends Markup
  final object Heading {
    sealed trait Level { def value: Int }
    final case object One extends Level { def value = 1 }
    final case object Two extends Level { def value = 2 }
    final case object Three extends Level { def value = 3 }
    final case object Four extends Level { def value = 4 }
    final case object Five extends Level { def value = 5 }
    final case object Six extends Level { def value = 6 }
  }
  // TODO: list blocks
}
