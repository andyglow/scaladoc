package scaladoc.parser

import scaladoc.Markup._
import scaladoc.utils._

import scala.collection.mutable.ListBuffer
import scala.util.Try
import scala.util.matching.Regex

/** Parses markup into a `Markup.Document`: the parser of every comment syntax. `ParseMarkup(text, syntax)` takes it to
  * the one the text is written in. scaladoc's wiki syntax is parsed here; Markdown by [[MarkdownBlocks]] and
  * [[MarkdownInlines]], which use what both syntaxes share from here.
  *
  * Wiki is parsed by a recursive descent parser like scaladoc's own: a document is a sequence of blocks (paragraphs, headings and code
  * blocks, lists, tables, rules), and a paragraph or a heading is inline markup, which nests: `'''bold ''italic'''''`. Unlike scaladoc, a
  * marker that isn't closed within its paragraph is text, so `x^2` and an unclosed backtick parse.
  *
  * HTML: block tags become their wiki syntax first (see `htmlToWiki`). The inline tags with a model equivalent become
  * that (`<b>` is bold, `<code>` monospace, `<a href>` a link), other safe tags an `HtmlTag`, and entities are decoded.
  * So are javadoc's inline tags: `{@code x}` is monospace, `{@link x}` a link.
  *
  * @param codeBlocks whether `{{{ ... }}}` is a code block; a heading's title is parsed without
  */
final class ParseMarkup private (text: String, log: Log, codeBlocks: Boolean) {

  private var pos = 0
  private val len = text.length

  private def eof: Boolean                    = pos >= len
  // HTML tags match whatever their case
  private def at(x: String, i: Int): Boolean =
    if (x.startsWith("</")) text.regionMatches(true, i, x, 0, x.length) else text.startsWith(x, i)

  private def lineEnd(i: Int): Int = {
    var j = i
    while (j < len && !text.charAt(j).isNL) j += 1
    j
  }

  private def firstNonSpace(i: Int): Int = {
    var j = i
    while (j < len && text.charAt(j).isSpace) j += 1
    j
  }

  private def isBlankLine(i: Int): Boolean = firstNonSpace(i) >= lineEnd(i)

  private def codeBlockAt(i: Int): Boolean = codeBlocks && at("{{{", i) && text.indexOf("}}}", i + 3) >= 0

  // a heading is a line of its own: `=`s, the title and as many `=`s again. Returns the level, the title and where
  // the line ends.
  private def headingAt(i: Int): Option[(Int, String, Int)] = {
    val start = firstNonSpace(i)
    val end   = lineEnd(start)
    val line  = text.substring(start, end).trim
    val level = line.takeWhile(_ == '=').length
    if (level > 0 && line.length > 2 * level && line.endsWith("=" * level))
      Some((level, line.substring(level, line.length - level), end))
    else None
  }

  // a horizontal rule: a line of four or more `-`. As in scaladoc, it starts a block, but doesn't end a paragraph.
  private def ruleAt(i: Int): Option[Int] = {
    val start = firstNonSpace(i)
    val end   = lineEnd(start)
    val line  = text.substring(start, end).trim
    if (line.length >= 4 && line.forall(_ == '-')) Some(end) else None
  }

  // a list item's line: indented, then `- `, `1. `, `i. `, `I. `, `a. ` or `A. `. Returns the indentation, the
  // numbering (none for `-`) and where the item's text starts.
  private def listItemAt(i: Int): Option[(Int, Option[OrderedList.Style], Int)] = {
    val start  = firstNonSpace(i)
    val indent = start - i
    if (indent == 0) None
    else if (at("- ", start)) Some((indent, None, start + 2))
    else OrderedList.styles.find(style => at(style.marker + ". ", start)).map { style =>
      (indent, Some(style), start + style.marker.length + 2)
    }
  }

  // the cells of a table row, a line like `| a | b |`. In a cell, `\|` is a `|`.
  private def rowAt(i: Int): Option[List[String]] = {
    val start = firstNonSpace(i)
    val line  = text.substring(start, lineEnd(start)).trim
    if (line.length >= 2 && line.startsWith("|") && line.endsWith("|") && !line.endsWith("\\|")) {
      val cells = ListBuffer.empty[String]
      val cell  = new StringBuilder
      var k     = 1
      while (k < line.length - 1) {
        val c = line.charAt(k)
        if (c == '\\' && line.charAt(k + 1) == '|') { cell += '|'; k += 2 }
        else if (c == '|') { cells += cell.toString; cell.setLength(0); k += 1 }
        else { cell += c; k += 1 }
      }
      cells += cell.toString
      Some(cells.toList)
    } else None
  }

  private val DelimiterCell = """\s*(:?)-+(:?)\s*""".r

  // a delimiter row: `---`, `:---`, `:---:` or `---:` cells
  private def alignmentsAt(i: Int): Option[List[Table.Alignment]] = rowAt(i) flatMap { cells =>
    val alignments = cells map {
      case DelimiterCell(":", ":") => Some(Table.Alignment.Center)
      case DelimiterCell("", ":")  => Some(Table.Alignment.Right)
      case DelimiterCell(_, _)     => Some(Table.Alignment.Left)
      case _                       => None
    }
    if (alignments.forall(_.isDefined)) Some(alignments.flatten) else None
  }

  // a table starts with a header row and a delimiter row of as many cells. Returns them and where they end.
  private def tableAt(i: Int): Option[(List[String], List[Table.Alignment], Int)] = rowAt(i) flatMap { header =>
    val next = lineEnd(i) + 1
    if (next >= len) None
    else alignmentsAt(next).filter(_.size == header.size).map(alignments => (header, alignments, lineEnd(next)))
  }

  // a paragraph ends with the text, at a paragraph separator, and at a line break before a blank line, a heading, a
  // list item or a table
  private def paragraphBreakAt(i: Int): Boolean =
    i >= len || text.charAt(i).isParSep || (text.charAt(i).isNL && {
      val next = i + 1
      next >= len || isBlankLine(next) || headingAt(next).isDefined || listItemAt(next).isDefined ||
      tableAt(next).isDefined
    })

  // ... and also where a code block starts, even in the middle of a line
  private def paragraphEndsAt(i: Int): Boolean = paragraphBreakAt(i) || codeBlockAt(i)

  def document(): Document = {
    val blocks = ListBuffer.empty[scaladoc.Markup]
    while (!eof) {
      skipBlankLines()
      if (!eof) {
        val from = pos
        block() foreach { b => log.info(s"- block: $b"); blocks += b }
        if (pos == from) pos += 1 // can't happen, but never loop forever
      }
    }
    Document(blocks.toList)
  }

  private def skipBlankLines(): Unit = {
    var more = true
    while (more && !eof) {
      if (text.charAt(pos).isNL) pos += 1
      else if (isBlankLine(pos)) pos = lineEnd(pos)
      else more = false
    }
  }

  private def block(): Option[scaladoc.Markup] = {
    val start = firstNonSpace(pos)
    if (codeBlockAt(start)) Some(code(start))
    else headingAt(pos) match {
      case Some((level, title, end)) =>
        pos = end
        // the model goes down to level 6; deeper headings are level 6, much like scaladoc renders them
        Some(Heading(Heading.levelOf(math.min(level, 6)), ParseMarkup.spans(title, log)))
      case None if ruleAt(pos).isDefined =>
        pos = ruleAt(pos).get
        Some(HorizontalRule)
      case None =>
        tableAt(pos) match {
          case Some((header, alignments, end)) => Some(table(header, alignments, end))
          case None =>
            listItemAt(pos) match {
              case Some((indent, style, _)) => Some(list(indent, style))
              case None                     => paragraph()
            }
        }
    }
  }

  // the rows after the header and the delimiter row, up to a line that isn't a row. Rows get as wide as the header.
  private def table(header: List[String], alignments: List[Table.Alignment], end: Int): Table = {
    def row(cells: List[String]) =
      Table.Row(cells.padTo(header.size, "").take(header.size).map(cell => Paragraph(ParseMarkup.spans(cell, log))))
    pos = end
    val rows = ListBuffer.empty[Table.Row]
    var more = true
    while (more && pos + 1 < len && text.charAt(pos).isNL) {
      rowAt(pos + 1) match {
        case Some(cells) => rows += row(cells); pos = lineEnd(pos + 1)
        case None        => more = false
      }
    }
    Table(row(header), alignments, rows.toList)
  }

  // the items with this indentation and numbering, and the lists nested in them. Blank lines between items don't
  // end a list.
  private def list(indent: Int, style: Option[OrderedList.Style]): ListBlock = {
    val items = ListBuffer.empty[ListBuffer[scaladoc.Markup]]
    var more  = true
    while (more) {
      val from = pos
      skipBlankLines()
      listItemAt(pos) match {
        case Some((`indent`, `style`, textAt)) =>
          pos = textAt
          items += (ListBuffer.empty[scaladoc.Markup] ++= paragraph().toList)
        case Some((deeper, nestedStyle, _)) if deeper > indent =>
          val nested = list(deeper, nestedStyle)
          if (items.isEmpty) items += ListBuffer.empty[scaladoc.Markup]
          items.last += nested
        case _ =>
          pos = from
          more = false
      }
    }
    val documents = items.toList.map(item => Document(item.toList))
    style.fold[ListBlock](UnorderedList(documents))(OrderedList(documents, _))
  }

  private def code(start: Int): CodeBlock = {
    val end = text.indexOf("}}}", start + 3)
    pos = end + 3
    CodeBlock(ParseMarkup.normalizeCode(text.substring(start + 3, end)))
  }

  private def paragraph(): Option[Paragraph] = {
    // whitespace around the text isn't part of it, and a paragraph of only whitespace isn't one
    val spans = spansUntil(None)._1.trim
    if (spans.exists(_.nonBlank)) Some(Paragraph(spans)) else None
  }

  // inline markup up to `stop` (which is left for the caller) or the end of the paragraph. `true` if it got to `stop`.
  // `outer` are the markers the enclosing markup waits for.
  private[parser] def spansUntil(stop: Option[String], outer: List[String] = Nil): (List[Span], Boolean) = {
    val spans = ListBuffer.empty[Span]
    val buf   = new StringBuilder

    def flush(): Unit = if (buf.nonEmpty) {
      spans += PlainText(buf.toString)
      buf.setLength(0)
    }

    // markup that can have markup inside, if its closing marker is in the paragraph
    def tryNested(marker: String)(make: List[Span] => Span): Boolean = {
      val from            = pos
      pos += marker.length
      val (inner, closed) = spansUntil(Some(marker), stop.toList ++ outer)
      if (closed) {
        flush()
        spans += make(inner)
        pos += marker.length
      } else pos = from
      closed
    }

    // ... without its closing marker, the opening one is text
    def nested(marker: String)(make: List[Span] => Span): Unit =
      if (!tryNested(marker)(make)) {
        buf ++= marker
        pos += marker.length
      }

    // markup whose content is taken as is
    def literal(open: String, close: String)(make: String => Span): Unit = {
      val from = pos + open.length
      var j    = from
      while (!paragraphBreakAt(j) && !at(close, j)) j += 1
      if (j < len && at(close, j)) {
        flush()
        spans += make(text.substring(from, j))
        pos = j + close.length
      } else {
        buf ++= open
        pos = from
      }
    }

    // `[[target]]` or `[[target title]]`, where the title can have markup. More brackets let the target have `]]`:
    // `[[[f(x:List[List[A]])*]]]` ends at `]]]`. Without its closing brackets in the paragraph, `[[` is text.
    def link(): Unit = {
      val from = pos
      var j    = pos
      while (j < len && text.charAt(j) == '[') j += 1
      val close  = "]" * (j - from)
      val target = j
      while (!paragraphBreakAt(j) && !at(close, j) && !text.charAt(j).isWhitespace) j += 1
      val closesRightAway = j > target && j < len && at(close, j)
      val hasTitle        = j > target && j < len && !paragraphBreakAt(j) && text.charAt(j).isWhitespace
      if (closesRightAway) {
        flush()
        spans += Link(text.substring(target, j), Nil)
        pos = j + close.length
      } else if (hasTitle) {
        pos = j
        while (!paragraphBreakAt(pos) && text.charAt(pos).isWhitespace) pos += 1
        val (title, closed) = spansUntil(Some(close), stop.toList ++ outer)
        if (closed) {
          flush()
          spans += Link(text.substring(target, j), title.trim)
          pos += close.length
        } else {
          pos = from + 2
          buf ++= "[["
        }
      } else {
        pos = from + 2
        buf ++= "[["
      }
    }

    // an HTML tag. The inline ones with a model equivalent, like <b>, become that, the other safe ones an HtmlTag. Any
    // other `<`, like the one of `List<String>`, is text.
    def html(): Unit = {
      val m = ParseMarkup.HtmlTagPattern.matcher(text)
      m.region(pos, len)
      if (!m.lookingAt()) { buf += '<'; pos += 1 }
      else {
        val raw     = m.group(0)
        val closing = m.group(1).nonEmpty
        val name    = m.group(2).toLowerCase
        val end     = m.end()
        def tag(): Unit = { flush(); spans += HtmlTag(raw); pos = end }
        def to(close: String)(make: List[Span] => Span): Unit = {
          val from            = pos
          pos = end
          val (inner, closed) = spansUntil(Some(close), stop.toList ++ outer)
          if (closed) { flush(); spans += make(inner); pos += close.length }
          else { pos = from; tag() }
        }
        if (closing) { if (ParseMarkup.SafeTags(name)) tag() else { buf += '<'; pos += 1 } }
        else name match {
          case "b" | "strong"  => to(s"</$name>")(Bold(_))
          case "i" | "em"      => to(s"</$name>")(Italic(_))
          case "u"             => to("</u>")(Underline(_))
          case "sup"           => to("</sup>")(Superscript(_))
          case "sub"           => to("</sub>")(Subscript(_))
          case "code" | "tt"   =>
            val close = s"</$name>"
            var j = end
            while (!paragraphBreakAt(j) && !at(close, j)) j += 1
            if (j < len && at(close, j)) { flush(); spans += Monospace(ParseMarkup.decodeEntities(text.substring(end, j))); pos = j + close.length }
            else tag()
          case "a" =>
            ParseMarkup.Href.findFirstMatchIn(m.group(3)) match {
              case Some(href) => to("</a>")(title => Link(ParseMarkup.decodeEntities(href.group(2)), title.trim))
              case None       => tag()
            }
          case "br"                               => buf += '\n'; pos = end
          case _ if ParseMarkup.SafeTags(name)    => tag()
          case _                                  => buf += '<'; pos += 1
        }
      }
    }

    // `&lt;`, `&#60;` and the like are the characters they stand for; an unknown one stays as it is
    def entity(): Unit = {
      val m = ParseMarkup.EntityPattern.matcher(text)
      m.region(pos, len)
      if (m.lookingAt()) { buf ++= ParseMarkup.decodeEntities(m.group(0)); pos = m.end() }
      else { buf += '&'; pos += 1 }
    }

    // javadoc's inline tags, which every syntax has; one that isn't closed in the paragraph is text
    def javadocTag(): Unit = ParseMarkup.javadocTagAt(text, pos, paragraphBreakAt, ParseMarkup.spans(_, log)) match {
      case Some((Left(content), end)) => buf ++= content; pos = end
      case Some((Right(span), end))   => flush(); spans += span; pos = end
      case None                       => buf ++= "{@"; pos += 2
    }

    var closed = false
    var done   = false
    while (!done) {
      if (paragraphEndsAt(pos)) done = true
      else if (stop.exists(at(_, pos))) {
        // in `''...''`, a `'''` is bold if it gets closed. Otherwise it ends the italic, unless it ends an enclosing
        // bold: `'''a ''b'''` is bold with a stray `''`, while in `'''a ''b'''''` the italic closes first.
        val tripleQuote = stop.contains("''") && at("'''", pos)
        if (!(tripleQuote && tryNested("'''")(Bold(_)))) {
          val quotes = text.iterator.drop(pos).takeWhile(_ == '\'').size
          closed = !(tripleQuote && outer.contains("'''") && quotes < 5)
          done = true
        }
      }
      else if (at("`", pos)) literal("`", "`")(Monospace(_))
      else if (at("'''", pos)) nested("'''")(Bold(_))
      else if (at("''", pos)) nested("''")(Italic(_))
      else if (at("__", pos)) nested("__")(Underline(_))
      else if (at("^", pos)) nested("^")(Superscript(_))
      else if (at(",,", pos)) nested(",,")(Subscript(_))
      else if (at("[[", pos)) link()
      else if (at("<", pos)) html()
      else if (at("&", pos)) entity()
      else if (at("{@", pos)) javadocTag()
      else {
        buf += text.charAt(pos)
        pos += 1
      }
    }
    flush()
    (spans.toList, closed)
  }
}

object ParseMarkup {

  /** The code of a code block as scaladoc has it: no blank lines at the start, no whitespace at the end, and without
    * the indentation that all of its non-blank lines share.
    */
  private[scaladoc] def normalizeCode(code: String): String = {
    val lines    = code.replaceAll("\\s+$", "").split("\n", -1).toList.dropWhile(_.trim.isEmpty)
    val nonBlank = lines.filter(_.trim.nonEmpty)
    val indent   = if (nonBlank.isEmpty) 0 else nonBlank.map(_.takeWhile(_ == ' ').length).min
    lines.map(line => if (line.trim.isEmpty) "" else line.drop(indent)).mkString("\n")
  }

  // the inline markup of a line, like a heading's title
  private[parser] def spans(line: String, log: Log): List[Span] =
    new ParseMarkup(line, log, codeBlocks = false).spansUntil(None)._1.trim

  /** Javadoc's inline tag at `i` of `text`, which every syntax has: `{@code x}` and `{@value x}` are monospace,
    * `{@literal x}` is text, `{@link x}` and `{@linkplain x}` are links with an optional label, which `label` parses,
    * and `{@docRoot}` goes. Braces in them must balance. Others, like `{@inheritDoc}`, and one that isn't closed before
    * `ends` are none. Text (`Left`) or a span, and where the tag ends.
    */
  private[parser] def javadocTagAt(
    text: String,
    i: Int,
    ends: Int => Boolean,
    label: String => List[Span]): Option[(Either[String, Span], Int)] = {
    val len = text.length
    var j   = i + 2
    while (j < len && text.charAt(j).isLetter) j += 1
    val name = text.substring(i + 2, j)
    while (j < len && text.charAt(j).isSpace) j += 1
    val from  = j
    var depth = 0
    while (!ends(j) && !(text.charAt(j) == '}' && depth == 0)) {
      if (text.charAt(j) == '{') depth += 1 else if (text.charAt(j) == '}') depth -= 1
      j += 1
    }
    if (!text.startsWith("{@", i) || !JavadocTags(name) || j >= len || text.charAt(j) != '}') None
    else {
      val content = text.substring(from, j)
      val tag: Either[String, Span] = name match {
        case "literal"        => Left(content)
        case "docRoot"        => Left("")
        case "code" | "value" => Right(Monospace(content))
        case _ =>
          val target = content.takeWhile(!_.isWhitespace)
          val text   = content.drop(target.length).trim
          Right(Link(target, if (text.isEmpty) Nil else label(text)))
      }
      Some((tag, j + 1))
    }
  }

  /** `text`, written in `syntax`, as a document. */
  def apply(text: String, syntax: scaladoc.CommentSyntax): Document = syntax match {
    case scaladoc.CommentSyntax.Wiki     => ParseMarkup(text)
    case scaladoc.CommentSyntax.Markdown => MarkdownBlocks(text)
  }

  def apply(chars: Array[Char], log: Log): Document = ParseMarkup(new String(chars), log)

  def apply(text: String, log: Log = Log.Noop): Document =
    new ParseMarkup(htmlToWiki(text), log, codeBlocks = true).document()

  // `<b>`, `</td>`, `<a href="x">`, `<br/>`: the closing slash, the name and the attributes
  private[parser] val HtmlTagPattern = java.util.regex.Pattern.compile("""<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\s[^<>]*)?)/?>""")

  private[parser] val Href = """(?i)href\s*=\s*(["'])(.*?)\1""".r

  private[parser] val JavadocTags: Set[String] = Set("code", "docRoot", "link", "linkplain", "literal", "value")

  // the tags scaladoc keeps, and <u>
  private[parser] val SafeTags: Set[String] = Set(
    "abbr", "acronym", "address", "area", "a", "bdo", "big", "blockquote", "br", "button", "b", "caption", "cite",
    "code", "col", "colgroup", "dd", "del", "dfn", "em", "fieldset", "form", "hr", "img", "input", "ins", "i", "kbd",
    "label", "legend", "link", "map", "object", "optgroup", "option", "param", "pre", "q", "samp", "select", "small",
    "span", "strong", "sub", "sup", "table", "tbody", "td", "textarea", "tfoot", "th", "thead", "tr", "tt", "u", "var")

  private val EntityRegex               = """&(?:#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);""".r
  private[parser] val EntityPattern     = EntityRegex.pattern
  private val NamedEntities             =
    Map("lt" -> "<", "gt" -> ">", "amp" -> "&", "quot" -> "\"", "apos" -> "'", "nbsp" -> " ")

  /** `&lt;`, `&#60;` and `&#x3c;` are `<`. Unknown entities stay as they are. */
  private[scaladoc] def decodeEntities(x: String): String =
    if (x.indexOf('&') < 0) x
    else EntityRegex.replaceAllIn(x, m => Regex.quoteReplacement(decodeEntity(m.matched)))

  private def decodeEntity(entity: String): String = {
    val body = entity.substring(1, entity.length - 1)
    def char(code: => Int) = Try(new String(Character.toChars(code))).getOrElse(entity)
    if (body.startsWith("#x") || body.startsWith("#X")) char(Integer.parseInt(body.drop(2), 16))
    else if (body.startsWith("#")) char(body.drop(1).toInt)
    else NamedEntities.getOrElse(body, entity)
  }

  private val CodeSegment  = """(?s)\{\{\{.*?\}\}\}""".r
  private val Pre          = """(?is)<pre(?:\s[^>]*)?>(.*?)</pre>""".r
  private val Code         = """(?i)</?code(?:\s[^>]*)?>""".r
  private val Comment      = """(?s)<!--.*?-->""".r
  private val ParagraphTag = """(?i)</?(?:p|div)(?:\s[^>]*)?/?>""".r
  private val HeadingOpen  = """(?i)<h([1-6])(?:\s[^>]*)?>""".r
  private val HeadingClose = """(?i)</h([1-6])>""".r
  private val ListItem     = """(?i)<li(?:\s[^>]*)?>""".r
  private val ListTags     = """(?i)</?(?:ul|ol)(?:\s[^>]*)?>|</li>""".r

  /** HTML block tags in wiki syntax, as scaladoc does: `<p>` and `<div>` are paragraph breaks, `<h1>` to `<h6>`
    * headings, `<li>` list items and `<pre>` code blocks; `<ul>`, `<ol>` and comments go. `{{{...}}}` stays as is.
    */
  private[scaladoc] def htmlToWiki(text: String): String = {
    def outsideCode(x: String)(f: String => String): String = {
      val sb   = new StringBuilder
      var last = 0
      CodeSegment.findAllMatchIn(x) foreach { m =>
        sb ++= f(x.substring(last, m.start))
        sb ++= m.matched
        last = m.end
      }
      sb ++= f(x.substring(last))
      sb.toString
    }
    if (text.indexOf('<') < 0) text
    else {
      val pre = outsideCode(text) { x =>
        Pre.replaceAllIn(x, m => Regex.quoteReplacement("\n{{{\n" + decodeEntities(Code.replaceAllIn(m.group(1), "")) + "\n}}}\n"))
      }
      outsideCode(pre) { x =>
        val blocks   = ParagraphTag.replaceAllIn(Comment.replaceAllIn(x, ""), "\n\n")
        val headings = HeadingClose.replaceAllIn(
          HeadingOpen.replaceAllIn(blocks, m => "\n" + "=" * m.group(1).toInt + " "),
          m => " " + "=" * m.group(1).toInt + "\n")
        ListTags.replaceAllIn(ListItem.replaceAllIn(headings, "\n - "), "")
      }
    }
  }
}
