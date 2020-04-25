package scaladocx.parse

import scaladocx._
import scaladocx.Markup._
import scaladocx.utils._

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
        if(from <= 0) ml.clear() else ml.remove(from, ml.length - from)
        handle(Paragraph(slice))
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

  private def checkNoParSep(text: String): Unit = {
    text.firstParSep match {
      case Some((pos, len)) => throwUnexpectedParSep(pos, len)
      case _                =>
    }
  }

  def run(): Document = {
    while (!isEOF) {
      val c = chars(pos)
      if (c == '`') {
        flush()
        next()
        val text = takeWhile(_ != '`')
        if (isEOF) throwUnexpectedEOF(None)
        checkNoParSep(text)
        handle(Monospace(text))
        next()
      } else if (c == '^') {
        flush()
        next()
        val text = takeWhile(_ != '^')
        if (isEOF) throwUnexpectedEOF(None)
        checkNoParSep(text)
        handle(Superscript(text))
        next()
      } else if (c == '\'' && remaining > 1 && chars(pos + 1) == '\'' && chars(pos + 2) == '\'') {
        flush()
        next()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil3 { case ('\'', '\'', '\'') => 2 }
        checkNoParSep(text)
        handle(Bold(text))
      } else if (c == '{' && remaining > 1 && chars(pos + 1) == '{' && chars(pos + 2) == '{') {
        flush()
        next()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil3 { case ('}', '}', '}') => 2 }
        handle(CodeBlock(text))
      } else if (c == '\'' && remaining > 0 && chars(pos + 1) == '\'') {
        flush()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil2 { case ('\'', '\'') => 1 }
        checkNoParSep(text)
        handle(Italic(text))
      } else if (c == '_' && remaining > 0 && chars(pos + 1) == '_') {
        flush()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil2 { case ('_', '_') => 1 }
        checkNoParSep(text)
        handle(Underline(text))
      } else if (c == ',' && remaining > 0 && chars(pos + 1) == ',') {
        flush()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil2 { case (',', ',') => 1 }
        checkNoParSep(text)
        handle(Subscript(text))
      } else if (c == '[' && remaining > 0 && chars(pos + 1) == '[') {
        flush()
        next()
        next()
        if (isEOF) throwUnexpectedEOF(None)
        val text = takeUntil2 { case (']', ']') => 1 }
        checkNoParSep(text)
        handle(Link(text))
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
          handle(Heading(level, clTrim.substring(tag.length, clTrim.length - tag.length)))
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