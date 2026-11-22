package scaladoc.parser

import scaladoc._
import scaladoc.Markup._
import scaladoc.utils._

import scala.collection.mutable


class ParseMarkup private (
  val chars: Array[Char],
  log: Log) extends Tokenizer {

  private val ml = mutable.ArrayBuffer.empty[Markup]

  private val buf = new StringBuilder

  def handle(m: Markup): Unit = {
    log.info(s"-  push: $m")
    ml += m
  }

  def wrapIntoParagraphs(force: Boolean): Unit = {
    // find most recent non SPAN element
    //
    var from  = ml.length - 1
    var brake = false
    while (!brake && from >= 0) {
      ml(from) match {
        case x: Span => log.info(s" - wrapp: $x => roll down"); from -= 1
        case x       => log.info(s" - wrapp: $x => brake at position $from"); from += 1; brake = true
      }
    }

    // make all elements following by the most recent non-span elements a paragraph
    if (force || from >= 0) {
      val slice = ml.slice(from, ml.length).toList map {_.asInstanceOf[Span]}
      if (slice.nonEmpty) {
        log.info(s" - wrapp: make paragraph from:")
        slice foreach { e =>
          log.info(s"   - $e")
        }
        if (from <= 0) ml.clear() else ml.remove(from, ml.length - from)
        handle(Paragraph(slice.trim))
        log.info(s" - wrapp: result:")
        ml foreach { e =>
          log.info(s"   - $e")
        }
      }
    }
  }

  def flush(): Unit = {
    if (buf.nonEmpty) {
      log.info(s"- flushing: [${buf.toString}]")

      // compact spans into a paragraph if paragraph separator found
      buf.toString.parSepTokenized foreach {
        case ParSep.Text(text) =>
          if (text.isEmpty) {
            log.info(s"- flush: text: empty: skipping")
          } else {
            log.info(s"- flush: text: [$text]")
            handle(PlainText(text))
          }
        case ParSep.Separator  => log.info(s"- flush: paragraph separator"); wrapIntoParagraphs(true)
      }

      buf.setLength(0)
    }
  }

  private def isAtString(x: String): Boolean =
    remaining >= x.length && x.indices.forall(i => chars(pos + i) == x.charAt(i))

  private def advanceTo(target: Int): Unit = while (pos < target && !isEOF) next()

  // where `close` starts, looking from `from` on: within the paragraph, or anywhere for code blocks, which can have
  // blank lines
  private def findClose(from: Int, close: String, sameParagraph: Boolean): Option[Int] = {
    val limit =
      if (!sameParagraph) chars.length
      else new String(chars, from, chars.length - from).firstParSep.fold(chars.length)(from + _._1)
    val idx = new String(chars, 0, limit).indexOf(close, from)
    if (idx < 0) None else Some(idx)
  }

  // the markup from `open` to `close`. Without a `close` the `open` marker is text, so a comment with `x^2` or an
  // unclosed backtick still parses
  private def span(open: String, close: String, sameParagraph: Boolean = true)(make: String => Markup): Unit = {
    val from = pos + open.length
    findClose(from, close, sameParagraph) match {
      case Some(end) =>
        flush()
        handle(make(new String(chars, from, end - from)))
        advanceTo(end + close.length)
      case None =>
        buf append open
        advanceTo(from)
    }
  }

  def run(): Document = {
    while (!isEOF) {
      val c = chars(pos)
      if (c == '`') {
        span("`", "`")(Monospace(_))
      } else if (c == '^') {
        span("^", "^")(Superscript(_))
      } else if (isAtString("'''")) {
        span("'''", "'''")(Bold(_))
      } else if (isAtString("{{{")) {
        span("{{{", "}}}", sameParagraph = false)(CodeBlock(_))
      } else if (isAtString("''")) {
        span("''", "''")(Italic(_))
      } else if (isAtString("__")) {
        span("__", "__")(Underline(_))
      } else if (isAtString(",,")) {
        span(",,", ",,")(Subscript(_))
      } else if (isAtString("[[")) {
        span("[[", "]]")(Link(_))
      } else if (c == '=' && isLineStart) {
        log.info("-> [" + new String(chars) + "]")
        val level = scanWhile(_ == '=') + 1
        val cl = currentLine
        val clTrim = cl.trim
        val tag = "=" * level
        val wsPrefix = {
          val eqIdx = cl.indexOf('=')
          cl.substring(0, eqIdx)
        }
        if (clTrim.endsWith(tag)) {
          flush()
          log.info("  -> curr line: [" + currentLine + "]")
          log.info("  -> trimmed  : [" + clTrim + "]")
          log.info("  -> tag      : [" + tag + "]")
          log.info("  -> wsPrefix : [" + wsPrefix + "]")

          // drop leading WS
          ml.lastOption match {
            case Some(PlainText(text)) if text.endsWith(wsPrefix) =>
              ml.remove(ml.length - 1)
              val effectiveText = text.dropRight(wsPrefix.length)
              if (effectiveText.nonEmpty) ml += PlainText(effectiveText)
            case _ =>
          }
          // the model goes down to level 6; deeper headings are level 6, much like scaladoc renders them
          handle(Heading(math.min(level, 6), clTrim.substring(tag.length, clTrim.length - tag.length)))
          skipWhile(_ => !isNL)
        } else {
          buf append c
          next()
        }
      } else {
        buf append c
        next()
      }
    }
    flush()

    wrapIntoParagraphs(false)

    Document(ml.toList)
  }
}

object ParseMarkup {

  def apply(chars: Array[Char], log: Log): Document = new ParseMarkup(chars, log).run()

  def apply(chars: String, log: Log = Log.Noop): Document = ParseMarkup(chars.toCharArray, log)
}