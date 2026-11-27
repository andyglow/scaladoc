package scaladoc.parser

import scaladoc.Markup._
import scaladoc.utils._

import scala.collection.mutable.ListBuffer
import scala.util.Try

/** Parses markup into a `Markup.Document`: the parser of every comment syntax. `ParseMarkup(text, syntax)` takes it to
  * the one the text is written in. scaladoc's wiki syntax is parsed here; Markdown by `MarkdownBlocks` and
  * `MarkdownInlines`, which use what both syntaxes share from here.
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

  // the next `}}}`, found once for all the `{{{`s before it
  private val codeEnd = new ParseMarkup.Next(text, "}}}")

  private def codeBlockAt(i: Int): Boolean = codeBlocks && at("{{{", i) && codeEnd.from(i + 3) >= 0

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

  // a delimiter row: `---`, `:---`, `:---:` or `---:` cells
  private def alignmentsAt(i: Int): Option[List[Table.Alignment]] = rowAt(i) flatMap { cells =>
    val alignments = cells.map(ParseMarkup.alignmentOf)
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
    def html(): Unit = ParseMarkup.htmlTagAt(text, pos) match {
      case None => buf += '<'; pos += 1
      case Some(ParseMarkup.TagAt(closing, tagName, attributes, end)) =>
        val raw  = text.substring(pos, end)
        val name = tagName.toLowerCase
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
            ParseMarkup.hrefIn(attributes) match {
              case Some(href) => to("</a>")(title => Link(ParseMarkup.decodeEntities(href), title.trim))
              case None       => tag()
            }
          case "br"                               => buf += '\n'; pos = end
          case _ if ParseMarkup.SafeTags(name)    => tag()
          case _                                  => buf += '<'; pos += 1
        }
    }

    // `&lt;`, `&#60;` and the like are the characters they stand for; an unknown one stays as it is
    def entity(): Unit = {
      val end = ParseMarkup.entityAt(text, pos)
      if (end >= 0) { buf ++= ParseMarkup.decodeEntity(text.substring(pos, end)); pos = end }
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
    val lines    = scaladoc.Markup.lines(code.stripTrailingSpace).dropWhile(_.trim.isEmpty)
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

  /** `text`, written in `syntax`, as a document. Markdown's HTML is cleaned first, as Scala 3's scaladoc does. */
  def apply(text: String, syntax: scaladoc.CommentSyntax): Document = syntax match {
    case scaladoc.CommentSyntax.Wiki     => ParseMarkup(text)
    case scaladoc.CommentSyntax.Markdown => MarkdownBlocks(cleanHtml(text))
  }

  // the tags Scala 3's scaladoc keeps in Markdown
  private val MarkdownSafeTags = Set(
    "a", "abbr", "address", "area", "blockquote", "br", "b", "caption", "cite", "code", "col", "colgroup", "dd",
    "del", "dfn", "em", "hr", "img", "ins", "i", "kbd", "label", "legend", "pre", "q", "samp", "small", "span",
    "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "var")

  /** HTML as Scala 3's scaladoc has it before it parses Markdown: a port of its `Cleaner`. The safe tags stay, `<p>`
    * and `<div>` are paragraph breaks, headings become `= ... =` and list items ` *  - `, and other tags go. Fenced
    * code (```` ``` ````) and `[[links]]` stay as they are.
    *
    * As in Scala 3, a tag's name goes up to its `>` or to the next space in the text, whichever comes first, and a `<`
    * with a space right after it, or without a `>` anywhere after it, is text. Names are case sensitive. So
    * `<https://x.y>`, and `<A>` in `List<A>`, go, as they do in Scala 3. Scala 3 cleans a whole comment, which always has
    * a space further on, before its closing marker; this cleans a tag's text, whose end counts as one.
    */
  private[parser] def cleanHtml(text: String): String =
    if (text.indexOf('<') < 0) text
    else {
      val out       = new StringBuilder
      val len       = text.length
      var i         = 0
      // the next `>` and the next space from `i` on, found once for many `<`s: -2 when there's none
      var nextGt    = -1
      var nextSpace = -1
      var inCode    = false
      var inLink    = false
      while (i < len) {
        if (inCode) {
          if (text.startsWith("```", i)) { out ++= "```"; i += 3; inCode = false }
          else { out += text.charAt(i); i += 1 }
        } else if (inLink) {
          if (text.startsWith("]]", i)) { out ++= "]]"; i += 2; inLink = false }
          else { out += text.charAt(i); i += 1 }
        } else if (text.startsWith("```", i)) { out ++= "```"; i += 3; inCode = true }
        else if (text.startsWith("[[", i)) { out ++= "[["; i += 2; inLink = true }
        else if (text.charAt(i) != '<') { out += text.charAt(i); i += 1 }
        else {
          if (nextGt != -2 && nextGt < i) { nextGt = text.indexOf('>', i); if (nextGt < 0) nextGt = -2 }
          if (nextSpace != -2 && nextSpace < i) { nextSpace = text.indexOf(' ', i); if (nextSpace < 0) nextSpace = -2 }
          val nameEnd = if (nextSpace < 0) len else nextSpace
          if (nameEnd == i + 1 || nextGt < 0) { out ++= "&lt;"; i += 1 }
          else {
            out ++= (text.substring(i + 1, math.min(nextGt, nameEnd)) match {
              case ""                    => "&lt;"
              case "p" | "div"           => "\n\n"
              case "h1"                  => "\n= "
              case "/h1"                 => " =\n"
              case "h2"                  => "\n== "
              case "/h2"                 => " ==\n"
              case "h3"                  => "\n=== "
              case "/h3"                 => " ===\n"
              case "h4" | "h5" | "h6"    => "\n==== "
              case "/h4" | "/h5" | "/h6" => " ====\n"
              case "li"                  => "\n *  - "
              case "/li"                 => ""
              case name if MarkdownSafeTags(name.stripPrefix("/")) => text.substring(i, nextGt + 1)
              case _                     => ""
            })
            i = nextGt + 1
          }
        }
      }
      out.toString
    }

  def apply(chars: Array[Char], log: Log): Document = ParseMarkup(new String(chars), log)

  def apply(text: String, log: Log = Log.Noop): Document =
    new ParseMarkup(htmlToWiki(text), log, codeBlocks = true).document()

  /** Where the next `x` starts, from a position on, or -1. A scanner that looks for a closer at each opener asks for
    * one position after another, and this finds each `x` once for all of them, so the scanner stays linear when the
    * closer is missing. Any position can be asked for, but going back means looking again.
    */
  private[parser] final class Next(text: String, x: String, ignoreCase: Boolean = false) {
    // the last position asked for, and the answer
    private var last  = Int.MaxValue
    private var found = -1

    private def at(j: Int): Boolean = if (ignoreCase) text.startsWithAsciiLower(x, j) else text.startsWith(x, j)

    def from(i: Int): Int = {
      if (i < last) {
        // up to `last`, where the answer is known
        val end = math.min(last, text.length)
        var j   = i
        while (j < end && !at(j)) j += 1
        if (j < end) found = j
        last = i
      } else if (found >= 0 && i > found) {
        var j = i
        while (j < text.length && !at(j)) j += 1
        found = if (j < text.length) j else -1
        last = i
      }
      found
    }
  }

  /** An HTML tag as the inline parser has it: whether it's a closing one, its name, its attributes (from the
    * whitespace after the name), and where it ends.
    */
  private[parser] final case class TagAt(closing: Boolean, name: String, attributes: String, end: Int)

  /** The HTML tag at `i`: `<b>`, `</td>`, `<a href="x">` or `<br/>`. A name is ASCII letters and digits, starting with
    * a letter. Attributes come after whitespace, and go up to the `>`, without a `<` in them.
    */
  private[parser] def htmlTagAt(text: String, i: Int): Option[TagAt] = {
    val len       = text.length
    val closing   = text.startsWith("</", i)
    val nameStart = if (closing) i + 2 else i + 1
    if (i >= len || text.charAt(i) != '<' || nameStart >= len || !text.charAt(nameStart).isAsciiLetter) None
    else {
      var nameEnd = nameStart + 1
      while (nameEnd < len && text.charAt(nameEnd).isAsciiAlnum) nameEnd += 1
      val name = text.substring(nameStart, nameEnd)
      if (nameEnd < len && text.charAt(nameEnd).isAsciiSpace) {
        var j = nameEnd + 1
        while (j < len && text.charAt(j) != '<' && text.charAt(j) != '>') j += 1
        if (j < len && text.charAt(j) == '>') Some(TagAt(closing, name, text.substring(nameEnd, j), j + 1)) else None
      } else if (text.startsWith("/>", nameEnd)) Some(TagAt(closing, name, "", nameEnd + 2))
      else if (text.startsWith(">", nameEnd)) Some(TagAt(closing, name, "", nameEnd + 1))
      else None
    }
  }

  // the characters that end a line, which an attribute's value doesn't go past
  private def isLineEnd(c: Char): Boolean = c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'

  /** The value of the first `href` in a tag's attributes that has one in quotes, `href="x"` or `href = 'x'`, in any
    * case. The value is on one line.
    */
  private[parser] def hrefIn(attributes: String): Option[String] = {
    val len = attributes.length
    def spaceEnd(from: Int): Int = {
      var j = from
      while (j < len && attributes.charAt(j).isAsciiSpace) j += 1
      j
    }
    def valueAt(i: Int): Option[String] = {
      val eq = spaceEnd(i + 4)
      if (eq >= len || attributes.charAt(eq) != '=') None
      else {
        val open = spaceEnd(eq + 1)
        if (open >= len || (attributes.charAt(open) != '"' && attributes.charAt(open) != '\'')) None
        else {
          val quote = attributes.charAt(open)
          var j     = open + 1
          while (j < len && attributes.charAt(j) != quote && !isLineEnd(attributes.charAt(j))) j += 1
          if (j < len && attributes.charAt(j) == quote) Some(attributes.substring(open + 1, j)) else None
        }
      }
    }
    var i                    = 0
    var href: Option[String] = None
    while (href.isEmpty && i + 4 <= len) {
      if (attributes.startsWithAsciiLower("href", i)) href = valueAt(i)
      i += 1
    }
    href
  }

  private[parser] val JavadocTags: Set[String] = Set("code", "docRoot", "link", "linkplain", "literal", "value")

  // the tags scaladoc keeps, and <u>
  private[parser] val SafeTags: Set[String] = Set(
    "abbr", "acronym", "address", "area", "a", "bdo", "big", "blockquote", "br", "button", "b", "caption", "cite",
    "code", "col", "colgroup", "dd", "del", "dfn", "em", "fieldset", "form", "hr", "img", "input", "ins", "i", "kbd",
    "label", "legend", "link", "map", "object", "optgroup", "option", "param", "pre", "q", "samp", "select", "small",
    "span", "strong", "sub", "sup", "table", "tbody", "td", "textarea", "tfoot", "th", "thead", "tr", "tt", "u", "var")

  private val NamedEntities =
    Map("lt" -> "<", "gt" -> ">", "amp" -> "&", "quot" -> "\"", "apos" -> "'", "nbsp" -> "\u00a0")

  /** Where the entity at `i` ends: `&lt;`, `&#60;` or `&#x3c;`, with ASCII letters and digits. -1 if there's none. */
  private[parser] def entityAt(text: String, i: Int): Int = {
    val len = text.length
    // where the run of `ok` characters from `from` ends, if it isn't empty and a `;` follows it
    def run(from: Int, ok: Char => Boolean): Int = {
      var j = from
      while (j < len && ok(text.charAt(j))) j += 1
      if (j > from && j < len && text.charAt(j) == ';') j + 1 else -1
    }
    if (i + 1 >= len || text.charAt(i) != '&') -1
    else if (text.charAt(i + 1) == '#') {
      val decimal = run(i + 2, _.isAsciiDigit)
      if (decimal >= 0) decimal
      else if (i + 2 < len && (text.charAt(i + 2) == 'x' || text.charAt(i + 2) == 'X')) run(i + 3, _.isHexDigit)
      else -1
    } else if (text.charAt(i + 1).isAsciiLetter) run(i + 1, _.isAsciiAlnum)
    else -1
  }

  /** `&lt;`, `&#60;` and `&#x3c;` are `<`. Unknown entities stay as they are. */
  private[scaladoc] def decodeEntities(x: String): String =
    if (x.indexOf('&') < 0) x
    else {
      val out = new StringBuilder(x.length)
      var i   = 0
      while (i < x.length) {
        val end = entityAt(x, i)
        if (end >= 0) { out ++= decodeEntity(x.substring(i, end)); i = end }
        else { out += x.charAt(i); i += 1 }
      }
      out.toString
    }

  // an entity, as `entityAt` finds it
  private[parser] def decodeEntity(entity: String): String = {
    val body = entity.substring(1, entity.length - 1)
    def char(code: => Int) = Try(new String(Character.toChars(code))).getOrElse(entity)
    if (body.startsWith("#x") || body.startsWith("#X")) char(Integer.parseInt(body.drop(2), 16))
    else if (body.startsWith("#")) char(body.drop(1).toInt)
    else NamedEntities.getOrElse(body, entity)
  }

  /** A table's delimiter cell: `---`, `:---`, `:---:` or `---:`, with whitespace around it. */
  private[parser] def alignmentOf(cell: String): Option[Table.Alignment] = {
    var from = 0
    var to   = cell.length
    while (from < to && cell.charAt(from).isAsciiSpace) from += 1
    while (to > from && cell.charAt(to - 1).isAsciiSpace) to -= 1
    val left  = from < to && cell.charAt(from) == ':'
    if (left) from += 1
    val right = to > from && cell.charAt(to - 1) == ':'
    if (right) to -= 1
    if (from == to || (from until to).exists(cell.charAt(_) != '-')) None
    else if (left && right) Some(Table.Alignment.Center)
    else if (right) Some(Table.Alignment.Right)
    else Some(Table.Alignment.Left)
  }

  /** HTML block tags in wiki syntax, as scaladoc does: `<p>` and `<div>` are paragraph breaks, `<h1>` to `<h6>`
    * headings, `<li>` list items and `<pre>` code blocks; `<ul>`, `<ol>` and comments go. `{{{...}}}` stays as is.
    *
    * One pass from left to right, where what starts first wins: tags in a comment, a `<pre>` or a `{{{...}}}` stay as
    * they are. Tag names are in any case.
    */
  private[scaladoc] def htmlToWiki(text: String): String =
    if (text.indexOf('<') < 0) text else new HtmlToWiki(text).run()

  /** The tags `htmlToWiki` knows: `<name>`, `<name attributes>` and, where they can close themselves, `<name/>`. The
    * attributes come after whitespace and go up to the next `>`.
    */
  private final class Tags(text: String) {
    private val gt = new Next(text, ">")

    /** Where the tag whose name ends at `i` ends, after its `>`, or -1. */
    def end(i: Int, selfClosing: Boolean = false): Int =
      if (i >= text.length) -1
      else if (text.charAt(i) == '>') i + 1
      else if (selfClosing && text.startsWith("/>", i)) i + 2
      else if (text.charAt(i).isAsciiSpace) { val k = gt.from(i + 1); if (k < 0) -1 else k + 1 }
      else -1
  }

  private final class HtmlToWiki(text: String) {
    private val len        = text.length
    private val out        = new StringBuilder(len)
    private val tags       = new Tags(text)
    private val codeEnd    = new Next(text, "}}}")
    private val commentEnd = new Next(text, "-->")
    private val preEnd     = new Next(text, "</pre>", ignoreCase = true)

    def run(): String = {
      var i = 0
      while (i < len) {
        val c = text.charAt(i)
        val end =
          if (c == '{' && text.startsWith("{{{", i)) code(i)
          else if (c == '<') tag(i)
          else -1
        if (end >= 0) i = end
        else { out += c; i += 1 }
      }
      out.toString
    }

    // `{{{...}}}`, as it is
    private def code(i: Int): Int = {
      val close = codeEnd.from(i + 3)
      if (close < 0) -1 else { out ++= text.substring(i, close + 3); close + 3 }
    }

    // a comment, a `<pre>` or a block tag at `i`, and where it ends; -1 if there's none
    private def tag(i: Int): Int = {
      val closing = text.startsWith("</", i)
      val name    = if (closing) i + 2 else i + 1
      // where the tag ends if it's `x`, or -1
      def is(x: String, selfClosing: Boolean = false): Int =
        if (text.startsWithAsciiLower(x, name)) tags.end(name + x.length, selfClosing) else -1
      def replace(end: Int, by: String): Int = { if (end >= 0) out ++= by; end }
      // `h1` to `h6`, or 0
      val level =
        if (text.startsWithAsciiLower("h", name) && name + 1 < len && text.charAt(name + 1) >= '1' && text.charAt(name + 1) <= '6')
          text.charAt(name + 1) - '0'
        else 0

      if (text.startsWith("<!--", i)) { val close = commentEnd.from(i + 4); if (close < 0) -1 else close + 3 }
      else if (!closing && is("pre") >= 0) pre(is("pre"))
      else if (level > 0 && !closing) replace(tags.end(name + 2), "\n" + "=" * level + " ")
      else if (level > 0) replace(if (text.startsWith(">", name + 2)) name + 3 else -1, " " + "=" * level + "\n")
      else {
        // the names don't start one another, so one at most is here
        val paragraph = math.max(is("p", selfClosing = true), is("div", selfClosing = true))
        val item      = if (closing) -1 else is("li")
        if (paragraph >= 0) replace(paragraph, "\n\n")
        else if (item >= 0) replace(item, "\n - ")
        else if (closing && text.startsWithAsciiLower("li>", name)) name + 3
        else math.max(is("ul"), is("ol"))
      }
    }

    // `<pre>...</pre>` as a code block, without `<code>` tags and with its entities decoded
    private def pre(open: Int): Int = {
      val close = preEnd.from(open)
      if (close < 0) -1
      else {
        out ++= "\n{{{\n" ++= decodeEntities(withoutCodeTags(text.substring(open, close))) ++= "\n}}}\n"
        close + "</pre>".length
      }
    }
  }

  // `<code>` and `</code>` go
  private def withoutCodeTags(code: String): String =
    if (code.indexOf('<') < 0) code
    else {
      val tags = new Tags(code)
      val out  = new StringBuilder(code.length)
      var i    = 0
      while (i < code.length) {
        val name = if (code.startsWith("</", i)) i + 2 else i + 1
        val end  =
          if (code.charAt(i) == '<' && code.startsWithAsciiLower("code", name)) tags.end(name + "code".length) else -1
        if (end >= 0) i = end
        else { out += code.charAt(i); i += 1 }
      }
      out.toString
    }
}
