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

  // trims a run of spans as one text: the whitespace around it goes, every line is trimmed, and whitespace between
  // two spans stays as a single space, so `a `b` c` stays that and ``a`b`c`` doesn't get spaces
  private[scaladoc] def trimSpans(spans: List[Span]): List[Span] = {
    val last = spans.length - 1
    spans.zipWithIndex.flatMap {
      case (PlainText(value), i) =>
        val text  = trimML(value)
        val lead  = i > 0 && value.headOption.exists(_.isWhitespace)
        val trail = i < last && value.lastOption.exists(_.isWhitespace)
        if (text.isEmpty) { if (lead || trail) List(new PlainText(" ") with Trimmed) else Nil }
        else List(new PlainText((if (lead) " " else "") + text + (if (trail) " " else "")) with Trimmed)
      case (x, _) =>
        List(x.trimmed)
    }
  }

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

  /** A span made of other spans: `''italic''`, `'''bold'''` and the like, which can nest. */
  sealed trait HasSpans { this: Span =>
    def spans: List[Span]
    override def isBlank: Boolean = spans.forall(_.isBlank)
    protected def spansString: String = spans.map(_.plainString).mkString
  }

  case class Italic(spans: List[Span]) extends Span with HasSpans {
    type Self = Italic
    override def plainString: String = s"''$spansString''"
    def trimmed: Self with Trimmed = new Italic(trimSpans(spans)) with Trimmed
  }

  object Italic {
    def apply(text: String): Italic        = Italic(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Italic  = Italic(x :: xs.toList)
  }

  case class Bold(spans: List[Span]) extends Span with HasSpans {
    type Self = Bold
    override def plainString: String = s"'''$spansString'''"
    def trimmed: Self with Trimmed = new Bold(trimSpans(spans)) with Trimmed
  }

  object Bold {
    def apply(text: String): Bold       = Bold(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Bold = Bold(x :: xs.toList)
  }

  case class Underline(spans: List[Span]) extends Span with HasSpans {
    type Self = Underline
    override def plainString: String = s"__${spansString}__"
    def trimmed: Self with Trimmed = new Underline(trimSpans(spans)) with Trimmed
  }

  object Underline {
    def apply(text: String): Underline       = Underline(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Underline = Underline(x :: xs.toList)
  }

  case class Superscript(spans: List[Span]) extends Span with HasSpans {
    type Self = Superscript
    override def plainString: String = s"^$spansString^"
    def trimmed: Self with Trimmed = new Superscript(trimSpans(spans)) with Trimmed
  }

  object Superscript {
    def apply(text: String): Superscript       = Superscript(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Superscript = Superscript(x :: xs.toList)
  }

  case class Subscript(spans: List[Span]) extends Span with HasSpans {
    type Self = Subscript
    override def plainString: String = s",,$spansString,,"
    def trimmed: Self with Trimmed = new Subscript(trimSpans(spans)) with Trimmed
  }

  object Subscript {
    def apply(text: String): Subscript       = Subscript(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Subscript = Subscript(x :: xs.toList)
  }

  /** A link to `target`, an entity like `scala.Option` or a URL like `https://scala-lang.org`, with an optional title:
    * `[[https://scala-lang.org Scala]]`.
    */
  case class Link(target: String, title: List[Span]) extends Span {
    type Self = Link
    override def isBlank: Boolean = target.forall(_.isWhitespace) && title.forall(_.isBlank)
    override def plainString: String =
      if (title.isEmpty) s"[[$target]]" else s"[[$target ${title.map(_.plainString).mkString}]]"
    def trimmed: Self with Trimmed = new Link(target.trim, trimSpans(title)) with Trimmed

    /** Whether the target is a URL (it has a scheme, like `https:`) rather than an entity. */
    def isUrl: Boolean = Link.SchemeUri.pattern.matcher(target).matches()
  }

  object Link {
    def apply(target: String): Link                    = Link(target, Nil)
    def apply(target: String, title: String): Link     = Link(target, List(PlainText(title)))
    private val SchemeUri                              = "[a-z][a-z0-9+.-]*:.*".r
  }

  /** An HTML tag with no equivalent in the model, like `<span class="x">` or `</td>`, as it was written. */
  case class HtmlTag(data: String) extends Span {
    type Self = HtmlTag
    override def isBlank: Boolean    = false
    override def plainString: String = data
    def trimmed: Self with Trimmed   = new HtmlTag(data) with Trimmed
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
      markup.map(_.plainString).mkString
    }
    def trimmed: Self with Trimmed = new Paragraph(trimSpans(markup)) with Trimmed
  }

  object Paragraph {
    def apply(x: Span, xs: Span*): Paragraph = Paragraph(x +: xs.toList)
  }

  case class Document(elements: List[Markup]) extends Markup {
    override type Self = Document
    override def isBlank: Boolean = elements.forall(_.isBlank)
    // spans next to each other are one text; anything else goes on a line of its own
    override def plainString: String = {
      val sb = new StringBuilder
      var prev: Option[Markup] = None
      elements foreach { x =>
        (prev, x) match {
          case (Some(_: Span), _: Span) =>
          case _                        => if (sb.nonEmpty) sb.append("\n")
        }
        sb.append(x.plainString)
        prev = Some(x)
      }
      sb.toString
    }
    def trimmed: Self with Trimmed = {
      val out  = List.newBuilder[Markup]
      var run  = List.empty[Span]
      def flushRun(): Unit = { out ++= trimSpans(run.reverse); run = Nil }
      elements foreach {
        case x: Span => run = x :: run
        case x       => flushRun(); out += x.trimmed
      }
      flushRun()
      new Document(out.result()) with Trimmed
    }
  }
  object Document {
    def apply(x: Markup, xs: Markup*): Document = Document(x +: xs.toList)
  }

  /** A list: its items are documents, with the item's text and the lists nested in it. */
  sealed trait ListBlock extends Markup {
    def items: List[Document]
    override def isBlank: Boolean = items.forall(_.isBlank)
    protected def marker(index: Int): String
    // one item a line, nested lists indented
    override def plainString: String = items.zipWithIndex.map { case (item, i) =>
      val lines = item.plainString.linesIterator.toList
      val first = s"${marker(i)} ${lines.headOption.getOrElse("")}"
      (first :: lines.drop(1).map("  " + _)).mkString("\n")
    }.mkString("\n")
  }

  /** `- item` lines. */
  case class UnorderedList(items: List[Document]) extends ListBlock {
    type Self = UnorderedList
    protected def marker(index: Int): String = "-"
    def trimmed: Self with Trimmed = new UnorderedList(items.map(_.trimmed)) with Trimmed
  }

  /** `1. item` lines, or `i.`, `I.`, `a.` and `A.` for the other numberings. */
  case class OrderedList(items: List[Document], style: OrderedList.Style) extends ListBlock {
    type Self = OrderedList
    protected def marker(index: Int): String = s"${style.marker}."
    def trimmed: Self with Trimmed = new OrderedList(items.map(_.trimmed), style) with Trimmed
  }

  object OrderedList {

    sealed abstract class Style(val marker: String) extends Product with Serializable

    case object Decimal    extends Style("1")
    case object LowerRoman extends Style("i")
    case object UpperRoman extends Style("I")
    case object LowerAlpha extends Style("a")
    case object UpperAlpha extends Style("A")

    val styles: List[Style] = List(Decimal, LowerRoman, UpperRoman, LowerAlpha, UpperAlpha)
  }

  /** A table: a header row, the alignment of each column and the data rows, all as wide as the header. */
  case class Table(header: Table.Row, alignments: List[Table.Alignment], rows: List[Table.Row]) extends Markup {
    type Self = Table
    override def isBlank: Boolean = (header :: rows).forall(_.cells.forall(_.isBlank))
    // `| a | b |` lines, with the alignment line after the header
    override def plainString: String = {
      def line(cells: List[String]) = cells.mkString("| ", " | ", " |")
      val delimiter = alignments.map {
        case Table.Alignment.Left   => "---"
        case Table.Alignment.Center => ":-:"
        case Table.Alignment.Right  => "--:"
      }
      (line(header.cells.map(_.plainString)) :: line(delimiter) :: rows.map(r => line(r.cells.map(_.plainString))))
        .mkString("\n")
    }
    def trimmed: Self with Trimmed = new Table(header.trimmed, alignments, rows.map(_.trimmed)) with Trimmed
  }

  object Table {

    /** A row; each of its cells is a paragraph. */
    final case class Row(cells: List[Paragraph]) {
      def trimmed: Row = Row(cells.map(_.trimmed))
    }

    sealed trait Alignment extends Product with Serializable

    object Alignment {
      case object Left   extends Alignment
      case object Center extends Alignment
      case object Right  extends Alignment
    }
  }

  /** A `----` line. */
  case object HorizontalRule extends Markup with Trimmed {
    type Self = HorizontalRule.type
    override def isBlank: Boolean      = false
    override def plainString: String   = "----"
    def trimmed: Self with Trimmed     = this
  }

  case class Heading(level: Heading.Level, spans: List[Span]) extends Markup {
    type Self = Heading
    override def isBlank: Boolean = spans.forall(_.isBlank)
    override def plainString: String = spans.map(_.plainString).mkString
    def trimmed: Self with Trimmed = new Heading(level, trimSpans(spans)) with Trimmed
  }

  object Heading {

    def apply(level: Level, text: String): Heading = Heading(level, List(PlainText(text)))

    sealed trait Level extends Product { def value: Int }

    case object One extends Level { def value = 1 }

    case object Two extends Level { def value = 2 }

    case object Three extends Level { def value = 3 }

    case object Four extends Level { def value = 4 }

    case object Five extends Level { def value = 5 }

    case object Six extends Level { def value = 6 }

    def levelOf(l: Int): Level = l match {
      case 1 => One
      case 2 => Two
      case 3 => Three
      case 4 => Four
      case 5 => Five
      case 6 => Six
      case _ => throw new IllegalArgumentException(s"Level $l is not supported")
    }

    def apply(l: Int, text: String): Heading = Heading(levelOf(l), text)
  }
}