package scaladoc.parser

import scaladoc.Markup._
import scaladoc.utils._

import scala.collection.mutable.ListBuffer

/** Parses scaladoc's wiki markup into a `Markup.Document`.
  *
  * A recursive descent parser like scaladoc's own: a document is a sequence of blocks (paragraphs, headings and code
  * blocks), and a paragraph or a heading is inline markup, which nests: `'''bold ''italic'''''`. Unlike scaladoc, a
  * marker that isn't closed within its paragraph is text, so `x^2` and an unclosed backtick parse.
  *
  * @param codeBlocks whether `{{{ ... }}}` is a code block; a heading's title is parsed without
  */
final class ParseMarkup private (text: String, log: Log, codeBlocks: Boolean) {

  private var pos = 0
  private val len = text.length

  private def eof: Boolean                    = pos >= len
  private def at(x: String, i: Int): Boolean = text.startsWith(x, i)

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

  // a paragraph ends with the text, at a paragraph separator, and at a line break before a blank line or a heading
  private def paragraphBreakAt(i: Int): Boolean =
    i >= len || text.charAt(i).isParSep || (text.charAt(i).isNL && {
      val next = i + 1
      next >= len || isBlankLine(next) || headingAt(next).isDefined
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
      case None =>
        paragraph()
    }
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
      else if (at("[[", pos)) literal("[[", "]]")(Link(_))
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

  def apply(chars: Array[Char], log: Log): Document = ParseMarkup(new String(chars), log)

  def apply(text: String, log: Log = Log.Noop): Document = new ParseMarkup(text, log, codeBlocks = true).document()
}
