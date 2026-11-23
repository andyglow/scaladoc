package scaladoc.parser

import scaladoc.Markup
import scaladoc.Markup._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Markdown's blocks, for [[ParseMarkup]], the parser of every syntax: CommonMark's blocks, with GitHub Flavored Markdown's tables and task list
  * items, and scaladoc's `{{{ ... }}}`, as Scala 3's scaladoc reads them.
  *
  * A recursive descent parser like [[ParseMarkup]]: a document is a sequence of blocks, each recognized at the start of
  * a line. A block quote's or a list item's content is its lines without the `>` or the item's indentation, parsed as a
  * document of its own. A paragraph's, a heading's and a table cell's text becomes inline markup once all blocks are
  * parsed, by [[MarkdownInlines]], since a link can refer to a definition further down.
  *
  * @param depth how deep in quotes and list items the text is: what would nest deeper than `MaxNesting` is text
  * @param refs  the link reference definitions found so far, which paragraphs start with
  * @param notes the labels of the footnotes found so far, normalized
  */
private[parser] final class MarkdownBlocks private (
  text: String,
  depth: Int,
  refs: mutable.Map[String, MarkdownInlines.Ref],
  notes: mutable.Set[String]) {
  import MarkdownBlocks._

  // the lines, with the tabs in their indentation expanded to the next multiple of 4
  private val lines = splitLines(text).map(expandIndentation)
  private var line  = 0

  private def eof: Boolean = line >= lines.length

  def blocks(): List[Block] = {
    val out = ListBuffer.empty[Block]
    while (!eof) {
      if (isBlank(lines(line))) line += 1
      else {
        val from = line
        out ++= block()
        if (line == from) line += 1 // can't happen, but never loop forever
      }
    }
    out.toList
  }

  private def block(): Option[Block] = {
    val n = line
    if (indentOf(lines(n)) >= 4) Some(indentedCode())
    else
      fenceAt(n) match {
        case Some((c, length, indent, info)) => Some(fencedCode(c, length, indent, info))
        case None if wikiCodeAt(n)           => Some(wikiCode())
        case None =>
          atxHeadingAt(n) match {
            case Some((level, title)) => line += 1; Some(HeadingBlock(level, title))
            case None if ruleAt(n)    => line += 1; Some(RuleBlock)
            case None =>
              htmlBlockAt(n) match {
                case Some(kind)                                => Some(htmlBlock(kind))
                case None if quoteAt(n) && depth < MaxNesting => Some(quote())
                case None if footnoteAt(n).isDefined && depth < MaxNesting => Some(footnote(footnoteAt(n).get))
                case None =>
                  listItemAt(n) match {
                    case Some(item) if depth + 2 <= MaxNesting => Some(list(item))
                    case _ =>
                      tableAt(n) match {
                        case Some((header, alignments)) => Some(table(header, alignments))
                        case None                       => paragraph()
                      }
                  }
              }
          }
      }
  }

  // ---- what starts at a line

  // `#`s, a space and the title, maybe with closing `#`s: the level and the title
  private def atxHeadingAt(n: Int): Option[(Int, String)] = {
    val s     = lines(n)
    val start = indentOf(s)
    val end   = runEnd(s, start, '#')
    val level = end - start
    if (start > 3 || level < 1 || level > 6 || (end < s.length && !isSpaceOrTab(s.charAt(end)))) None
    else Some((level, atxTitle(s.substring(end)).trim))
  }

  // 3 or more backticks or tildes: the character, how many, the fence's indentation and the info string. A backtick
  // fence's info string has no backticks.
  private def fenceAt(n: Int): Option[(Char, Int, Int, String)] = {
    val s      = lines(n)
    val indent = indentOf(s)
    if (indent > 3 || indent >= s.length || (s.charAt(indent) != '`' && s.charAt(indent) != '~')) None
    else {
      val c    = s.charAt(indent)
      val end  = runEnd(s, indent, c)
      val info = s.substring(end)
      // as Scala 3's flexmark has it, a tilde fence's info string has no backticks either
      if (end - indent < 3 || info.indexOf('`') >= 0) None
      else Some((c, end - indent, indent, MarkdownInlines.unescape(info.trim)))
    }
  }

  private def closingFenceAt(n: Int, c: Char, length: Int): Boolean = {
    val s      = lines(n)
    val indent = indentOf(s)
    val end    = runEnd(s, indent, c)
    indent <= 3 && end - indent >= length && isBlank(s.substring(end))
  }

  private def wikiCodeAt(n: Int): Boolean = {
    val s = lines(n)
    indentOf(s) <= 3 && s.startsWith("{{{", indentOf(s))
  }

  // 3 or more of the same `*`, `-` or `_`, maybe with spaces between, and nothing else
  private def ruleAt(n: Int): Boolean = {
    val s     = lines(n)
    val start = indentOf(s)
    start <= 3 && start < s.length && "*-_".indexOf(s.charAt(start)) >= 0 && {
      val c = s.charAt(start)
      s.substring(start).forall(x => x == c || isSpaceOrTab(x)) && s.count(_ == c) >= 3
    }
  }

  // a line of `=`s or `-`s under a paragraph: the heading's level
  private def setextAt(n: Int): Option[Int] = {
    val s     = lines(n)
    val start = indentOf(s)
    if (start > 3 || start >= s.length || (s.charAt(start) != '=' && s.charAt(start) != '-')) None
    else if (!isBlank(s.substring(runEnd(s, start, s.charAt(start))))) None
    else Some(if (s.charAt(start) == '=') 1 else 2)
  }

  private def quoteAt(n: Int): Boolean = {
    val s = lines(n)
    indentOf(s) <= 3 && indentOf(s) < s.length && s.charAt(indentOf(s)) == '>'
  }

  // which of the spec's seven kinds of HTML block starts here, 1 to 7
  private def htmlBlockAt(n: Int): Option[Int] = {
    val s = lines(n)
    if (indentOf(s) > 3) None else MarkdownHtml.blockAt(s, indentOf(s))
  }

  // `-`, `+`, `*`, or a number and `.` or `)`, then a space or the line's end
  private def listItemAt(n: Int): Option[ItemStart] = {
    val s      = lines(n)
    val indent = indentOf(s)
    if (indent > 3 || indent >= s.length) None
    else {
      val c = s.charAt(indent)
      val marker: Option[(ListMarker, Int)] =
        if ("*+-".indexOf(c) >= 0) Some((Bullet(c), indent + 1))
        else {
          var digits = indent
          while (digits < s.length && digits - indent < 9 && isAsciiDigit(s.charAt(digits))) digits += 1
          if (digits == indent || digits >= s.length || (s.charAt(digits) != '.' && s.charAt(digits) != ')')) None
          else Some((Ordered(s.charAt(digits), s.substring(indent, digits).toInt), digits + 1))
        }
      marker.flatMap { case (m, markerEnd) =>
        if (markerEnd < s.length && !isSpaceOrTab(s.charAt(markerEnd))) None
        else {
          // the tabs after the marker, as the spaces they take to the next multiple of 4
          val s = lines(n).substring(0, markerEnd) + expandFrom(lines(n).substring(markerEnd), markerEnd)
          val spaces = runEnd(s, markerEnd, ' ') - markerEnd
          val empty  = isBlank(s.substring(markerEnd))
          // the content starts after the spaces; with 5 or more, one is the separator, and the rest indent the content
          val contentOffset = if (empty || spaces >= 5) markerEnd + 1 else markerEnd + spaces
          val firstLine     = if (empty) "" else s.substring(math.min(contentOffset, s.length))
          Some(ItemStart(m, contentOffset, firstLine, empty))
        }
      }
    }
  }

  // `[^label]:`, as flexmark has footnotes: the label, and where its text starts on the line
  private def footnoteAt(n: Int): Option[(String, Int)] = {
    val s     = lines(n)
    val start = indentOf(s)
    if (start > 3 || !s.startsWith("[^", start)) None
    else {
      var end = start + 2
      while (end < s.length && s.charAt(end) != ']' && s.charAt(end) != '[' && !s.charAt(end).isWhitespace) end += 1
      if (end == start + 2 || !s.startsWith("]:", end)) None
      else Some((s.substring(start + 2, end), runEnd(s, end + 2, ' ')))
    }
  }

  // a header row and, on the next line, a delimiter row: the header's cells and the alignments. As Scala 3's flexmark
  // has it, the delimiter row needn't have as many cells as the header: the columns it has none for are aligned left.
  private def tableAt(n: Int): Option[(List[String], List[Table.Alignment])] =
    if (n + 1 >= lines.length || indentOf(lines(n + 1)) > 3 || lines(n + 1).indexOf('|') < 0) None
    else
      alignmentsOf(lines(n + 1)).flatMap { alignments =>
        val header = splitRow(lines(n))
        if (header.isEmpty) None
        else Some((header, alignments.padTo(header.length, Table.Alignment.Left).take(header.length)))
      }

  // whether a line ends a paragraph: a blank line, or a block that can start right after a paragraph's line. A list
  // can if it starts at 1 and has text; an HTML block of the 7th kind can't.
  private def interruptsAt(n: Int): Boolean =
    isBlank(lines(n)) || fenceAt(n).isDefined || wikiCodeAt(n) || atxHeadingAt(n).isDefined || ruleAt(n) ||
      htmlBlockAt(n).exists(_ < 7) || quoteAt(n) || tableAt(n).isDefined || footnoteAt(n).isDefined ||
      listItemAt(n).exists(item => !item.empty && (item.marker match {
        case Ordered(_, start) => start == 1
        case _                 => true
      }))

  // whether the first line is a paragraph's text, rather than the start of another block
  private def startsWithText: Boolean =
    lines.nonEmpty && !isBlank(lines(0)) && indentOf(lines(0)) < 4 && fenceAt(0).isEmpty && !wikiCodeAt(0) &&
      atxHeadingAt(0).isEmpty && !ruleAt(0) && htmlBlockAt(0).isEmpty && !quoteAt(0) && listItemAt(0).isEmpty &&
      footnoteAt(0).isEmpty

  // whether the line after `last` can go on lazily, without the quote's `>` or the item's indentation: `last`, without
  // the quotes' and items' markers it's in, is a paragraph's
  private def continuesLazily(last: String): Boolean = {
    var s    = last
    var more = true
    while (more && indentOf(s) < 4 && indentOf(s) < s.length) {
      val t = s.substring(indentOf(s))
      if (t.charAt(0) == '>') s = if (t.startsWith("> ")) t.substring(2) else t.substring(1)
      else if (t.length > 1 && "*+-".indexOf(t.charAt(0)) >= 0 && t.charAt(1) == ' ') s = t.substring(2)
      else {
        // `1.` or `1)`
        val digits = runWhile(t, 0, isAsciiDigit)
        if (digits > 0 && digits + 1 < t.length && ".)".indexOf(t.charAt(digits)) >= 0 && t.charAt(digits + 1) == ' ')
          s = t.substring(digits + 2)
        else more = false
      }
    }
    new MarkdownBlocks(s, MaxNesting, mutable.Map.empty, mutable.Set.empty).startsWithText
  }

  // a lazy line goes on its paragraph as is, whatever it would be on its own: indented, it can't start a block
  private def lazyLine(s: String): String = "    " + s.trim

  // ---- blocks

  // its lines, up to one that ends it; a line of `=`s or `-`s under them makes them a heading. The link reference
  // definitions it starts with are definitions, not text: a paragraph of only them is none.
  private def paragraph(): Option[Block] = {
    // without the spaces before each line; those after it are a hard break's
    val text = ListBuffer(lines(line).substring(indentOf(lines(line))))
    line += 1
    var heading: Option[Int] = None
    var more                 = true
    while (!eof && heading.isEmpty && more) {
      setextAt(line) match {
        case Some(level) => heading = Some(level); line += 1
        case None if interruptsAt(line) => more = false
        case None => text += lines(line).substring(indentOf(lines(line))); line += 1
      }
    }
    val rest = MarkdownInlines.takeReferences(text.mkString("\n"), refs)
    if (isBlank(rest.replace('\n', ' '))) {
      if (heading.isDefined) line -= 1 // then the underline is a line of its own
      None
    } else Some(heading.fold[Block](ParagraphBlock(rest))(HeadingBlock(_, rest)))
  }

  private def indentedCode(): Block = {
    val code = ListBuffer.empty[String]
    while (!eof && (indentOf(lines(line)) >= 4 || isBlank(lines(line)))) {
      code += (if (lines(line).length >= 4) lines(line).substring(4) else "")
      line += 1
    }
    // the blank lines after it aren't part of it
    while (code.nonEmpty && isBlank(code.last)) code.remove(code.length - 1)
    Code(code.mkString("\n"), None)
  }

  // up to a closing fence, or the end; its lines lose as much indentation as the fence has
  private def fencedCode(c: Char, length: Int, indent: Int, info: String): Block = {
    line += 1
    val code = ListBuffer.empty[String]
    while (!eof && !closingFenceAt(line, c, length)) {
      val s = lines(line)
      code += s.substring(math.min(indentOf(s), indent))
      line += 1
    }
    if (!eof) line += 1
    Code(code.mkString("\n"), Some(info.takeWhile(!isSpaceOrTab(_))).filter(_.nonEmpty))
  }

  // `{{{`, up to a line that starts with `}}}`
  private def wikiCode(): Block = {
    val first = lines(line)
    val code  = ListBuffer(first.substring(indentOf(first) + 3))
    line += 1
    while (!eof && !lines(line).trim.startsWith("}}}")) { code += lines(line); line += 1 }
    if (!eof) line += 1
    Code(normalizeCode(code.mkString("\n")), None)
  }

  // kinds 1 to 5 go up to their end marker, 6 and 7 to a blank line
  private def htmlBlock(kind: Int): Block = {
    val html = ListBuffer.empty[String]
    var done = false
    while (!eof && !done) {
      val s = lines(line)
      if (kind >= 6 && isBlank(s)) done = true
      else {
        html += s
        line += 1
        if (kind <= 5 && MarkdownHtml.blockEnds(kind, s)) done = true
      }
    }
    HtmlBlock(html.mkString("\n"))
  }

  // its lines without their `>`, and the lines that continue its last paragraph without one ("lazily")
  private def quote(): Block = {
    val content = ListBuffer.empty[String]
    var more    = true
    var lazily  = false // whether the last line went on lazily: then the paragraph goes on
    while (!eof && more) {
      val s = lines(line)
      if (quoteAt(line)) {
        val after = indentOf(s) + 1
        // a space after the `>` is part of it; a tab there, as many spaces as it takes, one of them
        val rest = expandFrom(s.substring(after), after)
        content += (if (rest.startsWith(" ")) rest.substring(1) else rest)
        line += 1
        lazily = false
      } else if (content.nonEmpty && (lazily || continuesLazily(content.last)) && !interruptsAt(line)) {
        content += lazyLine(s)
        line += 1
        lazily = true
      } else more = false
    }
    QuoteBlock(new MarkdownBlocks(content.mkString("\n"), depth + 1, refs, notes).blocks())
  }

  // a list's items: of the same kind of marker, with blank lines between them or not
  private def list(first: ItemStart): Block = {
    val items = ListBuffer.empty[List[Block]]
    var next  = Option(first)
    while (next.isDefined) {
      items += item(next.get)
      val from = line
      while (!eof && isBlank(lines(line))) line += 1
      next = if (eof || ruleAt(line)) None else listItemAt(line).filter(_.marker.sameList(first.marker))
      if (next.isEmpty) line = from
    }
    Items(first.marker, items.toList)
  }

  // an item's content: its first line's, and its other lines'
  private def item(start: ItemStart): List[Block] = content(start.firstLine, start.contentOffset, start.empty, depth + 2)

  // a footnote's text: on its line after the `]:`, and on the lines after indented by 4
  private def footnote(at: (String, Int)): Block = {
    val (label, textStart) = at
    notes += normalizeNote(label)
    val first = lines(line).substring(textStart)
    FootnoteBlock(label, content(first, 4, isBlank(first), depth + 1))
  }

  // the content of a list item or a footnote: its first line's, those indented as far as `contentOffset`, the blank
  // lines between them, and the lines that continue its last paragraph lazily
  private def content(firstLine: String, contentOffset: Int, empty: Boolean, depth: Int): List[Block] = {
    val content = ListBuffer(firstLine)
    line += 1
    // it can start with one blank line at most
    var more   = !(empty && (eof || isBlank(lines(line))))
    var lazily = false // whether the last line went on lazily: then the paragraph goes on
    while (!eof && more) {
      val s = lines(line)
      if (isBlank(s)) {
        lazily = false
        // blank lines are in the item if it goes on after them
        var k = line
        while (k < lines.length && isBlank(lines(k))) k += 1
        if (k < lines.length && indentOf(lines(k)) >= contentOffset) {
          while (line < k) { content += ""; line += 1 }
        } else more = false
      } else if (indentOf(s) >= contentOffset) {
        content += s.substring(contentOffset)
        line += 1
        lazily = false
      } else if (listItemAt(line).isEmpty && (lazily || continuesLazily(content.last)) && !interruptsAt(line)) {
        content += lazyLine(s)
        line += 1
        lazily = true
      } else more = false
    }
    new MarkdownBlocks(content.mkString("\n"), depth, refs, notes).blocks()
  }

  // its rows: the lines after the delimiter row that have a `|`, as Scala 3's flexmark has them, up to another block
  private def table(header: List[String], alignments: List[Table.Alignment]): Block = {
    line += 2
    val rows = ListBuffer.empty[List[String]]
    while (!eof && lines(line).indexOf('|') >= 0 && !(quoteAt(line) || fenceAt(line).isDefined || wikiCodeAt(line) ||
             atxHeadingAt(line).isDefined || ruleAt(line) || htmlBlockAt(line).exists(_ < 7))) {
      rows += splitRow(lines(line))
      line += 1
    }
    TableBlock(header, alignments, rows.toList)
  }
}

private[parser] object MarkdownBlocks {

  def apply(text: String): Document = withoutFrontMatter(text)

  // YAML front matter, `---` lines around anything at the start, as Scala 3's flexmark drops it
  private def withoutFrontMatter(text: String): Document = {
    val lines = splitLines(text)
    val close =
      if (lines.isEmpty || trimEnd(lines(0)) != "---") -1
      else lines.indexWhere(l => trimEnd(l) == "---" || trimEnd(l) == "...", 1)
    parse(if (close < 0) text else lines.drop(close + 1).mkString("\n"))
  }

  private def parse(text: String): Document = {
    val refs  = mutable.LinkedHashMap.empty[String, MarkdownInlines.Ref]
    val notes = mutable.Set.empty[String]
    toDocument(new MarkdownBlocks(text, 0, refs, notes).blocks(), refs, notes)
  }

  /** How deep quotes and lists nest at most, as markdown-it's `maxNesting`: what would nest deeper is text. The model
    * is a tree, and what walks it recurses.
    */
  final val MaxNesting = 100

  // the blocks while they're being parsed: their text becomes inline markup once they all are
  sealed trait Block
  final case class ParagraphBlock(text: String)                      extends Block
  final case class HeadingBlock(level: Int, text: String)            extends Block
  final case class Code(code: String, language: Option[String])      extends Block
  final case class HtmlBlock(html: String)                           extends Block
  case object RuleBlock                                               extends Block
  final case class QuoteBlock(content: List[Block])                  extends Block
  final case class FootnoteBlock(label: String, content: List[Block]) extends Block
  final case class Items(marker: ListMarker, items: List[List[Block]]) extends Block
  final case class TableBlock(header: List[String], alignments: List[Table.Alignment], rows: List[List[String]])
      extends Block

  sealed trait ListMarker {
    // items with different bullets, or different delimiters, are in different lists
    def sameList(other: ListMarker): Boolean = (this, other) match {
      case (Bullet(a), Bullet(b))         => a == b
      case (Ordered(a, _), Ordered(b, _)) => a == b
      case _                              => false
    }
  }
  final case class Bullet(char: Char)                   extends ListMarker
  final case class Ordered(delimiter: Char, start: Int) extends ListMarker

  /** A list item's first line: its marker, where its content starts, the content on it, and whether there's none. */
  final case class ItemStart(marker: ListMarker, contentOffset: Int, firstLine: String, empty: Boolean)

  /** A footnote's label as references to it are matched: case folded, as a link's. */
  def normalizeNote(label: String): String = MarkdownInlines.normalizeLabel(s"[$label]")

  private def toDocument(
    blocks: List[Block],
    refs: collection.Map[String, MarkdownInlines.Ref],
    notes: collection.Set[String]): Document = {
    def inlines(text: String): List[Span] = MarkdownInlines(text.trim, refs, notes)

    def toMarkup(b: Block): List[Markup] = b match {
      case ParagraphBlock(text) =>
        val spans = inlines(text)
        if (spans.isEmpty) Nil else List(Paragraph(spans))
      case HeadingBlock(level, text) => List(Heading(Heading.levelOf(level), inlines(text)))
      case Code(code, language)      => List(CodeBlock(code, language))
      case HtmlBlock(html)           => List(HtmlTag(html))
      case RuleBlock                 => List(HorizontalRule)
      case QuoteBlock(content)       => List(BlockQuote(toDocument(content, refs, notes)))
      case FootnoteBlock(label, content) => List(Footnote(label, toDocument(content, refs, notes)))
      case TableBlock(header, alignments, rows) =>
        def row(cells: List[String]) =
          Table.Row(cells.padTo(header.length, "").take(header.length).map(cell => Paragraph(inlines(cell))))
        List(Table(row(header), alignments, rows.map(row)))
      case Items(marker, items) =>
        val documents = items.map(taskItem)
        marker match {
          case _: Bullet  => List(UnorderedList(documents))
          case _: Ordered => List(OrderedList(documents, OrderedList.Decimal))
        }
    }

    // a GFM task list item: its first paragraph starts with `[ ]` or `[x]` and a space
    def taskItem(item: List[Block]): Document = item match {
      case ParagraphBlock(text) :: rest =>
        taskMarkerAt(text) match {
          case Some((checked, textStart)) =>
            // the space after the checkbox goes with the text: `[x] done` is `Checkbox(true)`, `PlainText(" done")`
            val spans = inlines(text.substring(textStart)) match {
              case PlainText(t) :: more => PlainText(" " + t) :: more
              case Nil                  => Nil
              case more                 => PlainText(" ") :: more
            }
            Document(Paragraph(Checkbox(checked) :: spans) :: toDocument(rest, refs, notes).elements)
          case None => toDocument(item, refs, notes)
        }
      case _ => toDocument(item, refs, notes)
    }

    // `[ ]` or `[x]`, spaces, and text: whether it's checked, and where the text starts
    def taskMarkerAt(s: String): Option[(Boolean, Int)] =
      if (s.length < 4 || s.charAt(0) != '[' || s.charAt(2) != ']' || " xX".indexOf(s.charAt(1)) < 0) None
      else {
        val text = runEnd(s, 3, ' ')
        if (text == 3 || text >= s.length) None else Some((s.charAt(1) != ' ', text))
      }

    Document(blocks.flatMap(toMarkup))
  }

  // ---- characters

  def isSpaceOrTab(c: Char): Boolean = c == ' ' || c == '\t'

  def isAsciiDigit(c: Char): Boolean = c >= '0' && c <= '9'

  def isBlank(s: String): Boolean = s.forall(isSpaceOrTab)

  /** How many spaces a line starts with. */
  def indentOf(s: String): Int = runEnd(s, 0, ' ')

  /** Where the run of characters that are `p` from `i` ends. */
  def runWhile(s: String, i: Int, p: Char => Boolean): Int = {
    var j = i
    while (j < s.length && p(s.charAt(j))) j += 1
    j
  }

  /** Where the run of `c` from `i` ends. */
  def runEnd(s: String, i: Int, c: Char): Int = {
    var j = i
    while (j < s.length && s.charAt(j) == c) j += 1
    j
  }

  /** `s` without trailing spaces and tabs. */
  def trimEnd(s: String): String = {
    var j = s.length
    while (j > 0 && isSpaceOrTab(s.charAt(j - 1))) j -= 1
    s.substring(0, j)
  }

  /** The lines of `s`, split at `\n`, without `\r`s; a last empty one isn't a line. */
  def splitLines(s: String): Array[String] = {
    val out   = Array.newBuilder[String]
    var start = 0
    var i     = s.indexOf('\n')
    while (i >= 0) {
      out += s.substring(start, i).filter(_ != '\r')
      start = i + 1
      i = s.indexOf('\n', start)
    }
    if (start < s.length) out += s.substring(start).filter(_ != '\r')
    out.result()
  }

  // the tabs `s` starts with, as spaces to the next multiple of 4, `s` being at `column` of its line
  private def expandFrom(s: String, column: Int): String =
    if (s.isEmpty || s.charAt(0) != '\t' && !(s.charAt(0) == ' ' && s.indexOf('\t') >= 0)) s
    else {
      val sb = new StringBuilder
      var i  = 0
      while (i < s.length && isSpaceOrTab(s.charAt(i))) {
        if (s.charAt(i) == '\t') sb.append(" " * (4 - (column + sb.length) % 4)) else sb.append(' ')
        i += 1
      }
      sb.append(s.substring(i)).toString
    }

  // the tabs in a line's indentation, as spaces to the next multiple of 4
  private def expandIndentation(s: String): String =
    if (s.isEmpty || !isSpaceOrTab(s.charAt(0)) || s.indexOf('\t') < 0) s
    else {
      val sb = new StringBuilder
      var i  = 0
      while (i < s.length && isSpaceOrTab(s.charAt(i))) {
        if (s.charAt(i) == '\t') sb.append(" " * (4 - sb.length % 4)) else sb.append(' ')
        i += 1
      }
      sb.append(s.substring(i)).toString
    }

  // an ATX heading's title without its closing `#`s: a run of them at the end, after a space, or all there is
  private def atxTitle(s: String): String = {
    val t = trimEnd(s)
    var j = t.length
    while (j > 0 && t.charAt(j - 1) == '#') j -= 1
    if (j == t.length) t
    else if (isBlank(t.substring(0, j))) ""
    else if (isSpaceOrTab(t.charAt(j - 1))) trimEnd(t.substring(0, j))
    else t
  }

  // as scaladoc has a `{{{ }}}` block's code: no blank lines at the start, no whitespace at the end, and without the
  // indentation all its non-blank lines share
  private def normalizeCode(code: String): String = {
    val lines    = splitLines(code).toList.map(trimEnd).dropWhile(_.isEmpty).reverse.dropWhile(_.isEmpty).reverse
    val nonBlank = lines.filter(_.nonEmpty)
    val indent   = if (nonBlank.isEmpty) 0 else nonBlank.map(indentOf).min
    lines.map(l => if (l.isEmpty) "" else l.substring(indent)).mkString("\n")
  }

  /** A GFM table's delimiter row, like `| :-- | --: |`: the alignment of each column. */
  def alignmentsOf(row: String): Option[List[Table.Alignment]] = {
    val aligned = splitRow(row).map { cell =>
      val t      = cell.trim
      val left   = t.startsWith(":")
      val right  = t.length > 1 && t.endsWith(":")
      val dashes = t.substring(if (left) 1 else 0, math.max(if (left) 1 else 0, t.length - (if (right) 1 else 0)))
      if (dashes.isEmpty || !dashes.forall(_ == '-')) None
      else if (left && right) Some(Table.Alignment.Center)
      else if (right) Some(Table.Alignment.Right)
      else Some(Table.Alignment.Left)
    }
    if (aligned.nonEmpty && aligned.forall(_.isDefined)) Some(aligned.flatten) else None
  }

  /** A table row's cells: split at the `|`s that aren't escaped, without the leading and trailing one. An escaped `|`
    * stays escaped, for the cell's inline markup: a `|` outside code, and `\|` in a code span, as in Scala 3.
    */
  def splitRow(row: String): List[String] = {
    val text  = row.trim
    val cells = ListBuffer.empty[String]
    val cell  = new StringBuilder
    var i     = if (text.startsWith("|")) 1 else 0
    while (i < text.length) {
      text.charAt(i) match {
        case '\\' if i + 1 < text.length && text.charAt(i + 1) == '|' => cell.append("\\|"); i += 2
        case '|' => cells += cell.toString.trim; cell.setLength(0); i += 1
        case c   => cell.append(c); i += 1
      }
    }
    if (cell.toString.trim.nonEmpty || !text.endsWith("|") || text == "|") cells += cell.toString.trim
    cells.toList
  }
}
