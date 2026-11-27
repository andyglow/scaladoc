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

  /** The lines of `x`, split at each `\n`. Empty ones count, at the end too: `"a\n"` is `a` and an empty line. */
  private[scaladoc] def lines(x: String): List[String] = {
    val lines = List.newBuilder[String]
    var from  = 0
    var nl    = x.indexOf('\n')
    while (nl >= 0) {
      lines += x.substring(from, nl)
      from = nl + 1
      nl = x.indexOf('\n', from)
    }
    lines += x.substring(from)
    lines.result()
  }

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

  /** Struck-through text: `~~gone~~` (Markdown). */
  case class Strikethrough(spans: List[Span]) extends Span with HasSpans {
    type Self = Strikethrough
    override def plainString: String = s"~~$spansString~~"
    def trimmed: Self with Trimmed = new Strikethrough(trimSpans(spans)) with Trimmed
  }

  object Strikethrough {
    def apply(text: String): Strikethrough       = Strikethrough(List(PlainText(text)))
    def apply(x: Span, xs: Span*): Strikethrough = Strikethrough(x :: xs.toList)
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
    def isUrl: Boolean = Link.hasScheme(target)
  }

  object Link {
    def apply(target: String): Link                    = Link(target, Nil)
    def apply(target: String, title: String): Link     = Link(target, List(PlainText(title)))

    // a scheme, `[a-z][a-z0-9+.-]*:`, and the rest on one line
    private def hasScheme(target: String): Boolean = {
      def schemeChar(c: Char) = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '+' || c == '.' || c == '-'
      def lineEnd(c: Char)    = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'
      var i = 1
      while (i < target.length && schemeChar(target.charAt(i))) i += 1
      target.nonEmpty && target.charAt(0) >= 'a' && target.charAt(0) <= 'z' && i < target.length &&
      target.charAt(i) == ':' && !target.exists(lineEnd)
    }
  }

  /** An image: `![alt](src)` (Markdown). `alt` is its text alternative, as plain text. */
  case class Image(src: String, alt: String) extends Span {
    type Self = Image
    override def isBlank: Boolean    = src.forall(_.isWhitespace) && alt.forall(_.isWhitespace)
    override def plainString: String = s"![$alt]($src)"
    def trimmed: Self with Trimmed   = new Image(src.trim, trimML(alt)) with Trimmed
  }

  object Image {
    def apply(src: String): Image = Image(src, "")
  }

  /** A line break inside a paragraph: a line that ends with two spaces or a `\` (Markdown). Other line ends in a
    * paragraph are just whitespace.
    */
  case object LineBreak extends Span with Trimmed {
    type Self = LineBreak.type
    override def isBlank: Boolean    = true
    override def plainString: String = "\n"
    def trimmed: Self with Trimmed   = this
  }

  /** The checkbox of a task list item, `- [x] done` or `- [ ] to do` (Markdown): the first span of the item's first
    * paragraph, as GitHub Flavored Markdown has it. Ordered lists can have them too.
    */
  case class Checkbox(checked: Boolean) extends Span {
    type Self = Checkbox
    override def isBlank: Boolean    = false
    override def plainString: String = if (checked) "[x]" else "[ ]"
    def trimmed: Self with Trimmed   = new Checkbox(checked) with Trimmed
  }

  /** A reference to a footnote, `[^1]` (Markdown). The footnote's text is the [[Footnote]] with the same label. */
  case class FootnoteReference(label: String) extends Span {
    type Self = FootnoteReference
    override def isBlank: Boolean    = false
    override def plainString: String = s"[^$label]"
    def trimmed: Self with Trimmed   = new FootnoteReference(label.trim) with Trimmed
  }

  /** An HTML tag with no equivalent in the model, like `<span class="x">` or `</td>`, as it was written. */
  case class HtmlTag(data: String) extends Span {
    type Self = HtmlTag
    override def isBlank: Boolean    = false
    override def plainString: String = data
    def trimmed: Self with Trimmed   = new HtmlTag(data) with Trimmed
  }

  /** A block of code: `{{{ ... }}}`, or in Markdown a fenced or an indented one. `language` is what a fence names,
    * like `scala` in ```` ```scala ````: the first word of its info string.
    */
  case class CodeBlock(value: String, language: Option[String] = None) extends Markup with HasValue {
    override type Self = CodeBlock
    override def plainString: String = s"{{{$value}}}"
    def trimmed: Self with Trimmed = new CodeBlock(trimML(value), language) with Trimmed
  }

  object CodeBlock {
    def apply(value: String, language: String): CodeBlock = CodeBlock(value, Some(language))
  }

  /** A quote: `> quoted` lines (Markdown). It holds blocks, so it can have paragraphs, lists, code and quotes of its
    * own.
    */
  case class BlockQuote(content: Document) extends Markup {
    type Self = BlockQuote
    override def isBlank: Boolean = content.isBlank
    // its lines, each after a `>`
    override def plainString: String =
      lines(content.plainString).map(line => if (line.isEmpty) ">" else s"> $line").mkString("\n")
    def trimmed: Self with Trimmed = new BlockQuote(content.trimmed) with Trimmed
  }

  object BlockQuote {
    def apply(x: Markup, xs: Markup*): BlockQuote = BlockQuote(Document(x +: xs.toList))
  }

  /** A footnote's text, `[^1]: The text.` (Markdown), where it is in the comment. [[FootnoteReference]]s with the same
    * label point to it.
    */
  case class Footnote(label: String, content: Document) extends Markup {
    type Self = Footnote
    override def isBlank: Boolean = content.isBlank
    // `[^1]: ` and the text, its next lines indented
    override def plainString: String = {
      val lines = Markup.lines(content.plainString)
      (s"[^$label]: ${lines.head}" :: lines.tail.map(line => if (line.isEmpty) "" else s"    $line")).mkString("\n")
    }
    def trimmed: Self with Trimmed = new Footnote(label.trim, content.trimmed) with Trimmed
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