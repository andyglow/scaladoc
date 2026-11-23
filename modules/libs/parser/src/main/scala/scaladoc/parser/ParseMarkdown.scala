package scaladoc.parser

import scaladoc.Markup
import scaladoc.Markup._

import scala.collection.mutable.ArrayBuffer

/** Markdown's block structure, as CommonMark specifies it (https://spec.commonmark.org), with GitHub Flavored
  * Markdown's tables and task list items, and scaladoc's `{{{ ... }}}` code blocks: what Scala 3's scaladoc reads.
  *
  * It follows the spec's parsing strategy, the one commonmark.js implements. A line first continues the blocks that
  * are open, from the outermost in: a block quote takes its `>`, a list item its indentation. What's left may open
  * new blocks, and the text that remains goes to the innermost one, or continues a paragraph "lazily". Blocks that a
  * line doesn't continue are closed.
  *
  * Inline content (of paragraphs, headings and table cells) is parsed by [[ParseMarkdown.inlines]].
  */
private[parser] final class ParseMarkdown private (input: String) {
  import ParseMarkdown._

  // the blocks while they're being built; `toMarkup` turns them into the model
  private sealed abstract class Block {
    var parent: Block = _
    val children      = ArrayBuffer.empty[Block]
    var open          = true

    /** Whether `b` can be a child: list items only go into lists, and lists only hold items. */
    def canContain(b: Block): Boolean = false

    /** Whether the text left on a line goes into this block (rather than into a new paragraph). */
    def acceptsLines: Boolean = false

    /** Whether a line continues this block: `Matched` (having consumed its prefix), `NotMatched`, or `Consumed` (the
      * line was this block's last, like a closing code fence).
      */
    def continues(): Continuation

    /** Called when the block is closed. */
    def finish(): Unit = ()
  }

  private abstract class TextBlock extends Block {
    val content = new StringBuilder
  }

  private final class DocumentBlock extends Block {
    override def canContain(b: Block): Boolean = !b.isInstanceOf[ItemBlock]
    def continues(): Continuation              = Matched
  }

  private final class QuoteBlock extends Block {
    override def canContain(b: Block): Boolean = !b.isInstanceOf[ItemBlock]
    def continues(): Continuation =
      if (!indented && peek(nextNonspace) == '>') {
        advanceNextNonspace()
        advanceOffset(1, columns = false)
        if (isSpaceOrTab(peek(offset))) advanceOffset(1, columns = true)
        Matched
      } else NotMatched
  }

  private final class ListBlock(val marker: ListMarker) extends Block {
    override def canContain(b: Block): Boolean = b.isInstanceOf[ItemBlock]
    def continues(): Continuation              = Matched
  }

  private final class ItemBlock(val markerOffset: Int, val padding: Int) extends Block {
    override def canContain(b: Block): Boolean = !b.isInstanceOf[ItemBlock]
    def continues(): Continuation =
      if (blank) {
        // an item that starts with a blank line ends at the next one
        if (children.isEmpty) NotMatched else { advanceNextNonspace(); Matched }
      } else if (indent >= markerOffset + padding) {
        advanceOffset(markerOffset + padding, columns = true)
        Matched
      } else NotMatched
  }

  private final class ParagraphBlock extends TextBlock {
    override def acceptsLines: Boolean = true
    def continues(): Continuation      = if (blank) NotMatched else Matched
  }

  private final class HeadingBlock(val level: Int, val text: String) extends Block {
    def continues(): Continuation = NotMatched
  }

  private final class RuleBlock extends Block {
    def continues(): Continuation = NotMatched
  }

  /** ```` ``` ```` or `~~~`, `fenceOffset` the fence's indentation, which the code's lines lose. */
  private final class FencedBlock(val fenceChar: Char, val fenceLength: Int, val fenceOffset: Int) extends TextBlock {
    var info = ""
    override def acceptsLines: Boolean = true
    def continues(): Continuation = {
      val closes = indent <= 3 && peek(nextNonspace) == fenceChar && {
        val run = line.iterator.drop(nextNonspace).takeWhile(_ == fenceChar).size
        run >= fenceLength && line.substring(nextNonspace + run).forall(isSpaceOrTab)
      }
      if (closes) { close(this); Consumed }
      else {
        var i = fenceOffset
        while (i > 0 && isSpaceOrTab(peek(offset))) { advanceOffset(1, columns = true); i -= 1 }
        Matched
      }
    }
    // the first line is the info string
    override def finish(): Unit = {
      val text = content.toString
      val nl   = text.indexOf('\n')
      info = unescape(if (nl < 0) text else text.substring(0, nl)).trim
      content.setLength(0)
      if (nl >= 0) content.append(text.substring(nl + 1))
    }
  }

  /** scaladoc's `{{{ ... }}}`, which Scala 3 reads in Markdown too. */
  private final class WikiCodeBlock extends TextBlock {
    override def acceptsLines: Boolean = true
    def continues(): Continuation =
      if (indent <= 3 && line.startsWith("}}}", nextNonspace)) { close(this); Consumed } else Matched
  }

  private final class IndentedCodeBlock extends TextBlock {
    override def acceptsLines: Boolean = true
    def continues(): Continuation =
      if (indent >= 4) { advanceOffset(4, columns = true); Matched }
      else if (blank) { advanceNextNonspace(); Matched }
      else NotMatched
  }

  /** One of the spec's seven kinds of HTML block; 6 and 7 end at a blank line, the others at their end marker. */
  private final class HtmlBlock(val kind: Int) extends TextBlock {
    override def acceptsLines: Boolean = true
    def continues(): Continuation      = if (blank && (kind == 6 || kind == 7)) NotMatched else Matched
  }

  /** A GFM table; each line that continues it is a row. */
  private final class TableBlock(val header: List[String], val alignments: List[Table.Alignment]) extends Block {
    val rows                           = ArrayBuffer.empty[List[String]]
    override def acceptsLines: Boolean = true
    def continues(): Continuation      = if (blank) NotMatched else Matched
  }

  // where the parser is, as in commonmark.js: `offset` in the line's characters, `column` with tabs expanded to the
  // next multiple of 4, and whether a tab was only partly consumed (when a container takes some of its columns)
  private var line                 = ""
  private var offset               = 0
  private var column               = 0
  private var nextNonspace         = 0
  private var nextNonspaceColumn   = 0
  private var indent               = 0
  private var indented             = false
  private var blank                = false
  private var partiallyConsumedTab = false

  private val document              = new DocumentBlock
  private var tip: Block            = document
  private var oldTip: Block         = document
  private var lastMatched: Block    = document
  private var allClosed             = true

  private def peek(i: Int): Char = if (i < line.length) line.charAt(i) else EOL

  private def findNextNonspace(): Unit = {
    var i    = offset
    var cols = column
    while (i < line.length && isSpaceOrTab(line.charAt(i))) {
      cols += (if (line.charAt(i) == '\t') 4 - (cols % 4) else 1)
      i += 1
    }
    blank = i >= line.length
    nextNonspace = i
    nextNonspaceColumn = cols
    indent = nextNonspaceColumn - column
    indented = indent >= 4
  }

  private def advanceNextNonspace(): Unit = {
    offset = nextNonspace
    column = nextNonspaceColumn
    partiallyConsumedTab = false
  }

  private def advanceOffset(count: Int, columns: Boolean): Unit = {
    var left = count
    while (left > 0 && offset < line.length) {
      if (line.charAt(offset) == '\t') {
        val toTab = 4 - (column % 4)
        if (columns) {
          partiallyConsumedTab = toTab > left
          val advance = math.min(left, toTab)
          column += advance
          if (!partiallyConsumedTab) offset += 1
          left -= advance
        } else {
          partiallyConsumedTab = false
          column += toTab
          offset += 1
          left -= 1
        }
      } else {
        partiallyConsumedTab = false
        offset += 1
        column += 1
        left -= 1
      }
    }
  }

  private def addLine(): Unit = tip match {
    // the rest of a delimiter row, once the table has started, is empty: it's not a row
    case table: TableBlock => if (!line.substring(offset).forall(isSpaceOrTab)) table.rows += splitRow(line.substring(offset))
    case b: TextBlock =>
      if (partiallyConsumedTab) {
        // the rest of a tab a container took part of
        offset += 1
        b.content.append(" " * (4 - (column % 4)))
      }
      b.content.append(line.substring(offset)).append('\n')
    case _ =>
  }

  private def addChild(b: Block): Block = {
    while (!tip.canContain(b)) close(tip)
    b.parent = tip
    tip.children += b
    tip = b
    b
  }

  private def close(b: Block): Unit = {
    b.open = false
    b.finish()
    tip = b.parent
  }

  private def closeUnmatchedBlocks(): Unit = if (!allClosed) {
    while (oldTip ne lastMatched) {
      val parent = oldTip.parent
      close(oldTip)
      oldTip = parent
    }
    allClosed = true
  }

  private def replace(old: Block, by: Block): Unit = {
    val parent = old.parent
    parent.children(parent.children.indexOf(old)) = by
    by.parent = parent
    tip = by
  }

  // block starts, tried in this order; each says whether it started a container (more starts may follow on the same
  // line), a leaf (the rest of the line is its content), or nothing
  private def blockQuoteStart(container: Block): Start =
    if (!indented && peek(nextNonspace) == '>') {
      advanceNextNonspace()
      advanceOffset(1, columns = false)
      if (isSpaceOrTab(peek(offset))) advanceOffset(1, columns = true)
      closeUnmatchedBlocks()
      addChild(new QuoteBlock)
      ContainerStart
    } else NoStart

  private def atxHeadingStart(container: Block): Start =
    if (indented) NoStart
    else
      AtxHeading.findPrefixMatchOf(line.substring(nextNonspace)) match {
        case Some(m) =>
          advanceNextNonspace()
          advanceOffset(m.matched.length, columns = false)
          closeUnmatchedBlocks()
          val text = line.substring(offset).replaceAll("^[ \t]*#+[ \t]*$", "").replaceAll("[ \t]+#+[ \t]*$", "")
          addChild(new HeadingBlock(m.group(1).length, text))
          advanceOffset(line.length - offset, columns = false)
          LeafStart
        case None => NoStart
      }

  private def fencedCodeStart(container: Block): Start =
    if (indented) NoStart
    else
      CodeFence.findPrefixMatchOf(line.substring(nextNonspace)) match {
        case Some(m) =>
          val fence = m.matched
          closeUnmatchedBlocks()
          addChild(new FencedBlock(fence.charAt(0), fence.length, indent))
          advanceNextNonspace()
          advanceOffset(fence.length, columns = false)
          LeafStart
        case None => NoStart
      }

  private def wikiCodeStart(container: Block): Start =
    if (!indented && line.startsWith("{{{", nextNonspace)) {
      closeUnmatchedBlocks()
      addChild(new WikiCodeBlock)
      advanceNextNonspace()
      advanceOffset(3, columns = false)
      LeafStart
    } else NoStart

  private def htmlBlockStart(container: Block): Start =
    if (indented || peek(nextNonspace) != '<') NoStart
    else {
      val rest = line.substring(nextNonspace)
      HtmlBlockOpen.indexWhere(_.pattern.matcher(rest).lookingAt()) match {
        // the 7th kind can't interrupt a paragraph
        case i if i >= 0 && !(i == 6 && container.isInstanceOf[ParagraphBlock]) =>
          closeUnmatchedBlocks()
          addChild(new HtmlBlock(i + 1))
          LeafStart
        case _ => NoStart
      }
    }

  private def tableStart(container: Block): Start = container match {
    case para: ParagraphBlock if !indented =>
      val delimiter = line.substring(nextNonspace)
      val lines     = para.content.toString.split("\n").toList
      (delimiter.indexOf('|') >= 0, alignmentsOf(delimiter)) match {
        case (true, Some(alignments)) =>
          val header = splitRow(lines.last)
          if (header.length != alignments.length) NoStart
          else {
            closeUnmatchedBlocks()
            val table = new TableBlock(header, alignments)
            if (lines.length == 1) replace(para, table)
            else {
              // the lines before the header stay a paragraph
              para.content.setLength(0)
              para.content.append(lines.init.mkString("", "\n", "\n"))
              close(para)
              addChild(table)
            }
            advanceOffset(line.length - offset, columns = false)
            LeafStart
          }
        case _ => NoStart
      }
    case _ => NoStart
  }

  private def setextHeadingStart(container: Block): Start = container match {
    case para: ParagraphBlock if !indented =>
      SetextUnderline.findFirstMatchIn(line.substring(nextNonspace)) match {
        case Some(m) =>
          closeUnmatchedBlocks()
          val level = if (m.matched.charAt(0) == '=') 1 else 2
          replace(para, new HeadingBlock(level, para.content.toString.trim))
          advanceOffset(line.length - offset, columns = false)
          LeafStart
        case None => NoStart
      }
    case _ => NoStart
  }

  private def thematicBreakStart(container: Block): Start =
    if (!indented && ThematicBreak.pattern.matcher(line.substring(nextNonspace)).matches()) {
      closeUnmatchedBlocks()
      addChild(new RuleBlock)
      advanceOffset(line.length - offset, columns = false)
      LeafStart
    } else NoStart

  private def listItemStart(container: Block): Start = listMarker(container) match {
    case Some((marker, markerOffset, padding)) =>
      closeUnmatchedBlocks()
      tip match {
        case list: ListBlock if list.marker.sameList(marker) =>
        case _                                               => addChild(new ListBlock(marker))
      }
      addChild(new ItemBlock(markerOffset, padding))
      ContainerStart
    case None => NoStart
  }

  private def indentedCodeStart(container: Block): Start =
    if (indented && !tip.isInstanceOf[ParagraphBlock] && !blank) {
      advanceOffset(4, columns = true)
      closeUnmatchedBlocks()
      addChild(new IndentedCodeBlock)
      LeafStart
    } else NoStart

  private val starts: List[Block => Start] = List(
    blockQuoteStart,
    atxHeadingStart,
    fencedCodeStart,
    wikiCodeStart,
    htmlBlockStart,
    tableStart,
    setextHeadingStart,
    thematicBreakStart,
    listItemStart,
    indentedCodeStart
  )

  /** A list marker at the line's next non-space, and the item's marker offset and padding: where its content starts.
    * Moves past the marker and the spaces after it.
    */
  private def listMarker(container: Block): Option[(ListMarker, Int, Int)] =
    if (indent >= 4) None
    else {
      val rest = line.substring(nextNonspace)
      val found: Option[(ListMarker, Int)] =
        if (rest.nonEmpty && "*+-".indexOf(rest.charAt(0)) >= 0) Some((Bullet(rest.charAt(0)), 1))
        else
          OrderedMarker.findPrefixMatchOf(rest).flatMap { m =>
            // a list can interrupt a paragraph only if it starts at 1
            if (container.isInstanceOf[ParagraphBlock] && m.group(1) != "1") None
            else Some((Ordered(m.group(2).charAt(0), m.group(1).toInt), m.matched.length))
          }
      found.flatMap { case (marker, length) =>
        val after = peek(nextNonspace + length)
        if (after != EOL && !isSpaceOrTab(after)) None
        // an empty item can't interrupt a paragraph
        else if (container.isInstanceOf[ParagraphBlock] && line.substring(nextNonspace + length).forall(isSpaceOrTab)) None
        else {
          val markerOffset = indent
          advanceNextNonspace()
          advanceOffset(length, columns = true)
          val spacesStartColumn = column
          val spacesStartOffset = offset
          advanceOffset(1, columns = true)
          while (column - spacesStartColumn < 5 && isSpaceOrTab(peek(offset))) advanceOffset(1, columns = true)
          val blankItem         = peek(offset) == EOL
          val spacesAfterMarker = column - spacesStartColumn
          val padding =
            if (spacesAfterMarker >= 5 || spacesAfterMarker < 1 || blankItem) {
              // the content starts one space after the marker: the rest is indentation, like indented code's
              column = spacesStartColumn
              offset = spacesStartOffset
              if (isSpaceOrTab(peek(offset))) advanceOffset(1, columns = true)
              length + 1
            } else length + spacesAfterMarker
          Some((marker, markerOffset, padding))
        }
      }
    }

  private def incorporate(ln: String): Unit = {
    line = ln
    offset = 0
    column = 0
    blank = false
    partiallyConsumedTab = false

    // 1. the open blocks the line continues
    var container: Block = document
    oldTip = tip
    var done      = false
    var consumed  = false
    while (!done && container.children.nonEmpty && container.children.last.open) {
      val last = container.children.last
      findNextNonspace()
      last.continues() match {
        case Matched    => container = last
        case NotMatched => done = true
        case Consumed   => done = true; consumed = true
      }
    }
    if (consumed) return

    allClosed = container eq oldTip
    lastMatched = container

    // 2. the blocks it starts; tables, like paragraphs, can be interrupted by other blocks
    var matchedLeaf = container.acceptsLines && !container.isInstanceOf[ParagraphBlock] &&
      !container.isInstanceOf[TableBlock]
    while (!matchedLeaf) {
      findNextNonspace()
      starts.iterator.map(_(container)).find(_ != NoStart) match {
        case Some(ContainerStart) => container = tip
        case Some(_)              => container = tip; matchedLeaf = true
        case None                 => advanceNextNonspace(); matchedLeaf = true
      }
    }

    // 3. what's left of it: a paragraph's continuation (even "lazily", without the prefixes of the blocks it's in), a
    // line of the innermost block, or a new paragraph
    if (!allClosed && !blank && tip.isInstanceOf[ParagraphBlock]) addLine()
    else {
      closeUnmatchedBlocks()
      if (container.acceptsLines) {
        addLine()
        container match {
          case html: HtmlBlock if html.kind <= 5 && HtmlBlockClose(html.kind - 1).findFirstIn(line.substring(offset)).nonEmpty =>
            close(html)
          case _ =>
        }
      } else if (offset < line.length && !blank) {
        addChild(new ParagraphBlock)
        advanceNextNonspace()
        addLine()
      }
    }
  }

  def run(): Document = {
    // a trailing newline doesn't make a last, empty line
    val lines = input.split("\n", -1)
    val count = if (lines.nonEmpty && lines.last.isEmpty) lines.length - 1 else lines.length
    var i = 0
    while (i < count) { incorporate(lines(i).replace("\r", "")); i += 1 }
    while (tip ne null) close(tip)
    toDocument(document)
  }

  private def toDocument(b: Block): Document = Document(b.children.toList.flatMap(toMarkup))

  private def toMarkup(b: Block): List[Markup] = b match {
    case p: ParagraphBlock =>
      val spans = inlines(p.content.toString)
      if (spans.isEmpty) Nil else List(Paragraph(spans))
    case h: HeadingBlock      => List(Heading(Heading.levelOf(h.level), inlines(h.text)))
    case _: RuleBlock         => List(HorizontalRule)
    case q: QuoteBlock        => List(BlockQuote(toDocument(q)))
    case f: FencedBlock       => List(CodeBlock(dropLastNewline(f.content.toString), f.info.split("[ \t]+").headOption.filter(_.nonEmpty)))
    case c: IndentedCodeBlock => List(CodeBlock(c.content.toString.replaceAll("(?:\n[ \t]*)+\\z", "").stripSuffix("\n")))
    case c: WikiCodeBlock     => List(CodeBlock(ParseMarkup.normalizeCode(c.content.toString)))
    case h: HtmlBlock         => List(HtmlTag(dropLastNewline(h.content.toString)))
    case t: TableBlock =>
      val width = t.header.length
      def row(cells: List[String]) =
        Table.Row(cells.padTo(width, "").take(width).map(cell => Paragraph(inlines(cell))))
      List(Table(row(t.header), t.alignments, t.rows.toList.map(row)))
    case l: ListBlock =>
      val items = l.children.toList.collect { case item: ItemBlock => taskItem(toDocument(item), item) }
      l.marker match {
        case _: Bullet  => List(UnorderedList(items))
        case _: Ordered => List(OrderedList(items, OrderedList.Decimal))
      }
    case _ => Nil
  }

  // a GFM task list item: its first paragraph starts with `[ ]` or `[x]` and a space
  private def taskItem(doc: Document, item: ItemBlock): Document = item.children.headOption match {
    case Some(p: ParagraphBlock) =>
      TaskMarker.findPrefixMatchOf(p.content.toString) match {
        case Some(m) =>
          val checkbox = Checkbox(checked = m.group(1) != " ")
          val rest     = inlines(p.content.toString.substring(m.end))
          // the space after the checkbox goes with the text: `[x] done` is `Checkbox(true)`, `PlainText(" done")`
          val text = rest match {
            case PlainText(t) :: more => PlainText(" " + t) :: more
            case Nil                  => Nil
            case more                 => PlainText(" ") :: more
          }
          val first = Paragraph(checkbox :: text)
          Document(first :: doc.elements.drop(1))
        case None => doc
      }
    case _ => doc
  }
}

private[parser] object ParseMarkdown {

  def apply(text: String): Document = new ParseMarkdown(text).run()

  /** The inline markup of a paragraph's, a heading's or a table cell's text. For now just its text, without the
    * spaces around its lines.
    */
  def inlines(text: String): List[Span] = {
    val lines = text.split("\n").map(_.trim).filter(_.nonEmpty)
    if (lines.isEmpty) Nil else List(PlainText(lines.mkString("\n")))
  }

  private sealed trait Continuation
  private case object Matched    extends Continuation
  private case object NotMatched extends Continuation
  private case object Consumed   extends Continuation

  private sealed trait Start
  private case object NoStart        extends Start
  private case object ContainerStart extends Start
  private case object LeafStart      extends Start

  private sealed trait ListMarker {
    // items with different bullets, or different delimiters, are in different lists
    def sameList(other: ListMarker): Boolean = (this, other) match {
      case (Bullet(a), Bullet(b))         => a == b
      case (Ordered(a, _), Ordered(b, _)) => a == b
      case _                              => false
    }
  }
  private final case class Bullet(char: Char)                 extends ListMarker
  private final case class Ordered(delimiter: Char, start: Int) extends ListMarker

  private val EOL = '\u0000'

  private def isSpaceOrTab(c: Char): Boolean = c == ' ' || c == '\t'

  private val AtxHeading      = "(#{1,6})(?:[ \t]+|$)".r
  private val CodeFence       = "`{3,}(?!.*`)|~{3,}".r
  private val SetextUnderline = "^(?:=+|-+)[ \t]*$".r
  private val ThematicBreak   = "(?:\\*[ \t]*){3,}|(?:_[ \t]*){3,}|(?:-[ \t]*){3,}".r
  private val OrderedMarker   = "(\\d{1,9})([.)])".r
  private val TaskMarker      = "^[ \t]*\\[([ xX])\\][ \t]+(?=\\S)".r

  private val BlockTagNames =
    "address|article|aside|base|basefont|blockquote|body|caption|center|col|colgroup|dd|details|dialog|dir|div|dl|dt|" +
      "fieldset|figcaption|figure|footer|form|frame|frameset|h[123456]|head|header|hr|html|iframe|legend|li|link|main|" +
      "menu|menuitem|nav|noframes|ol|optgroup|option|p|param|section|source|summary|table|tbody|td|tfoot|th|thead|" +
      "title|tr|track|ul"
  private val AttributeValue = "(?:[^\"'=<>`\\x00-\\x20]+|'[^']*'|\"[^\"]*\")"
  private val Attribute      = s"(?:\\s+[a-zA-Z_:][a-zA-Z0-9_.:-]*(?:\\s*=\\s*$AttributeValue)?)"
  private val OpenTag        = s"<[A-Za-z][A-Za-z0-9-]*$Attribute*\\s*/?>"
  private val CloseTag       = "</[A-Za-z][A-Za-z0-9-]*\\s*>"

  // the spec's seven kinds of HTML block: how each starts, and how the first five end
  private val HtmlBlockOpen = List(
    "(?i)<(?:script|pre|style)(?:\\s|>|$)",
    "<!--",
    "<[?]",
    "<![A-Z]",
    "<!\\[CDATA\\[",
    s"(?i)</?(?:$BlockTagNames)(?:\\s|/?>|$$)",
    s"(?:$OpenTag|$CloseTag)\\s*$$"
  ).map(_.r)
  private val HtmlBlockClose = List("(?i)</(?:script|pre|style)>", "-->", "\\?>", ">", "\\]\\]>").map(_.r)

  private val Alignment = "^[ \t]*(:?)-+(:?)[ \t]*$".r

  /** A GFM table's delimiter row, like `| :-- | --: |`: the alignment of each column. */
  def alignmentsOf(row: String): Option[List[Table.Alignment]] = {
    val cells = splitRow(row)
    val aligned = cells.map { cell =>
      Alignment.findFirstMatchIn(cell).map { m =>
        (m.group(1).nonEmpty, m.group(2).nonEmpty) match {
          case (true, true)  => Table.Alignment.Center
          case (false, true) => Table.Alignment.Right
          case _             => Table.Alignment.Left
        }
      }
    }
    if (cells.nonEmpty && aligned.forall(_.nonEmpty)) Some(aligned.flatten) else None
  }

  /** A table row's cells: split at the `|`s that aren't escaped, without the leading and trailing one. An escaped `|`
    * is a `|` in the cell.
    */
  def splitRow(row: String): List[String] = {
    val text  = row.trim
    val cells = ArrayBuffer.empty[String]
    val cell  = new StringBuilder
    var i     = if (text.startsWith("|")) 1 else 0
    while (i < text.length) {
      text.charAt(i) match {
        case '\\' if i + 1 < text.length && text.charAt(i + 1) == '|' => cell.append('|'); i += 2
        case '|' => cells += cell.toString.trim; cell.setLength(0); i += 1
        case c   => cell.append(c); i += 1
      }
    }
    if (cell.toString.trim.nonEmpty || !text.endsWith("|") || text == "|") cells += cell.toString.trim
    cells.toList
  }

  private def dropLastNewline(x: String): String = x.stripSuffix("\n")

  private val Escapable = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

  /** Backslash escapes of ASCII punctuation, and entities, as in a code fence's info string. */
  def unescape(x: String): String = {
    val sb = new StringBuilder
    var i  = 0
    while (i < x.length) {
      val c = x.charAt(i)
      if (c == '\\' && i + 1 < x.length && Escapable.indexOf(x.charAt(i + 1)) >= 0) { sb.append(x.charAt(i + 1)); i += 2 }
      else { sb.append(c); i += 1 }
    }
    ParseMarkup.decodeEntities(sb.toString)
  }
}
