package scaladoc.parser

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite
import scaladoc.Markup
import scaladoc.Markup._

import scala.collection.mutable
import scala.io.Source

/** The examples of the GitHub Flavored Markdown spec (CommonMark and GitHub's extensions), in
  * `src/test/resources/commonmark/gfm-spec.txt`. The spec is © John MacFarlane and GitHub, under CC BY-SA 4.0
  * (http://creativecommons.org/licenses/by-sa/4.0/); see the README next to it.
  *
  * Each example's Markdown is parsed into the model by `MarkdownBlocks`, the parser itself: without the HTML cleaning
  * Scala 3's scaladoc does first, which `ParseMarkup` does for Markdown comments, and `MarkdownHtmlCleaningSpec` tests.
  * Then the model is rendered as HTML the way the spec's reference
  * implementation does, to compare with the spec's. A few things the model leaves out on purpose are normalized away on
  * both sides: whether a list is tight or loose, where an ordered list starts, a column's default alignment, and the
  * titles of links and images.
  *
  * `passing.txt`, next to the spec, lists the examples that pass, and the suite fails if any other set does: one that
  * starts failing is a regression, and one that starts passing goes on the list. On a mismatch, the actual list is
  * written to `target/commonmark-passing.txt`.
  */
class CommonMarkSpecSuite extends AnyFunSuite {
  import CommonMarkSpecSuite._

  private val dir      = new File("./modules/libs/parser/src/test/resources/commonmark")
  private val examples = parseSpec(read(new File(dir, "gfm-spec.txt")))

  private def passes(e: Example): Boolean =
    try normalize(render(MarkdownBlocks(e.markdown))) == normalize(e.html)
    catch { case _: Exception => false }

  test("the spec's examples that pass are the ones in passing.txt") {
    val passing = examples.filter(passes).map(_.number).toSet
    val listed  = read(new File(dir, "passing.txt")).split("\\s+").filter(_.nonEmpty).map(_.toInt).toSet

    val bySection = examples.groupBy(_.section).toList.sortBy(_._2.head.number).map { case (section, xs) =>
      f"${xs.count(x => passing(x.number))}%4d / ${xs.size}%-4d $section"
    }
    info(s"${passing.size} of ${examples.size} examples pass\n${bySection.mkString("\n")}")

    // `-Dcommonmark.show=Tables,Lists` (sections, or `all`) prints those sections' failing examples
    sys.props.get("commonmark.show").foreach { show =>
      val sections = show.split(",").map(_.trim).toSet
      examples
        .filter(e => !passing(e.number) && (show == "all" || sections.exists(s => e.section.startsWith(s))))
        .foreach { e =>
          val actual =
            try normalize(render(MarkdownBlocks(e.markdown)))
            catch { case ex: Exception => s"!! $ex" }
          info(s"#${e.number} (${e.section})\n--- markdown\n${e.markdown}--- expected\n${normalize(e.html)}\n--- actual\n$actual\n")
        }
    }

    val regressed = listed -- passing
    val fixed     = passing -- listed
    if (regressed.nonEmpty || fixed.nonEmpty) {
      val out = new File("./modules/libs/parser/target/commonmark-passing.txt")
      out.getParentFile.mkdirs()
      Files.write(out.toPath, passing.toList.sorted.mkString("\n").getBytes(StandardCharsets.UTF_8))
      fail(
        s"no longer pass: ${regressed.toList.sorted.mkString(", ")}\n" +
          s"pass now: ${fixed.toList.sorted.mkString(", ")}\n" +
          s"the actual list is in $out")
    }
  }
}

object CommonMarkSpecSuite {

  final case class Example(number: Int, section: String, markdown: String, html: String)

  private val Fence = "````````````````````````````````"

  private def read(f: File): String = {
    val src = Source.fromFile(f, "UTF-8")
    try src.mkString finally src.close()
  }

  /** The examples: between ```` ```...``` example ```` and ```` ```...``` ````, Markdown, then `.`, then HTML. `→` is a tab. */
  def parseSpec(spec: String): List[Example] = {
    val out     = List.newBuilder[Example]
    var section = ""
    var number  = 0
    val lines   = spec.split("\n", -1).iterator
    while (lines.hasNext) {
      val line = lines.next()
      if (line.startsWith(Fence + " example")) {
        val md   = new StringBuilder
        val html = new StringBuilder
        var part = md
        var done = false
        while (!done && lines.hasNext) {
          val l = lines.next()
          if (l == Fence) done = true
          else if (l == "." && (part eq md)) part = html
          else part.append(l.replace('→', '\t')).append('\n')
        }
        number += 1
        out += Example(number, section, md.toString, html.toString)
      } else if (line.startsWith("#")) section = line.dropWhile(_ == '#').trim
    }
    out.result()
  }

  private def escape(x: String): String =
    x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  /** The model as HTML, the way the spec's reference implementation writes it. */
  def render(m: Markup): String = m match {
    case Document(elements) => elements.map(render).mkString
    case Paragraph(spans)   => s"<p>${spans.map(span).mkString}</p>\n"
    case Heading(level, spans) =>
      s"<h${level.value}>${spans.map(span).mkString}</h${level.value}>\n"
    case CodeBlock(code, language) =>
      val cls = language.fold("")(l => s""" class="language-${escape(l)}"""")
      s"<pre><code$cls>${escape(if (code.isEmpty) "" else code + "\n")}</code></pre>\n"
    case BlockQuote(content) => s"<blockquote>\n${render(content)}</blockquote>\n"
    case UnorderedList(items) => s"<ul>\n${items.map(item).mkString}</ul>\n"
    case OrderedList(items, _) => s"<ol>\n${items.map(item).mkString}</ol>\n"
    case HorizontalRule       => "<hr />\n"
    case HtmlTag(data)        => data + "\n"
    case Table(header, alignments, rows) =>
      def cells(row: Table.Row, tag: String) = row.cells.zip(alignments).map { case (cell, a) =>
        val align = a match {
          case Table.Alignment.Left   => ""
          case Table.Alignment.Center => " align=\"center\""
          case Table.Alignment.Right  => " align=\"right\""
        }
        s"<$tag$align>${cell.markup.map(span).mkString}</$tag>\n"
      }.mkString
      val body = if (rows.isEmpty) "" else s"<tbody>\n${rows.map(r => s"<tr>\n${cells(r, "td")}</tr>\n").mkString}</tbody>\n"
      s"<table>\n<thead>\n<tr>\n${cells(header, "th")}</tr>\n</thead>\n$body</table>\n"
    case x: Span => span(x)
    case other   => sys.error(s"can't render $other")
  }

  private def item(doc: Document): String = s"<li>${render(doc)}</li>\n"

  private def span(x: Span): String = x match {
    case PlainText(text)          => escape(text)
    case Monospace(text)          => s"<code>${escape(text)}</code>"
    case Italic(spans)            => s"<em>${spans.map(span).mkString}</em>"
    // as cmark-gfm's renderer: no `<strong>` right inside another
    case Bold(List(inner: Bold))  => span(inner)
    case Bold(spans)              => s"<strong>${spans.map(inStrong).mkString}</strong>"
    case Strikethrough(spans)     => s"<del>${spans.map(span).mkString}</del>"
    case Link(target, title)      => s"""<a href="${escape(encodeUri(target))}">${title.map(span).mkString}</a>"""
    case Image(src, alt)          => s"""<img src="${escape(encodeUri(src))}" alt="${escape(alt)}" />"""
    case LineBreak                => "<br />\n"
    case Checkbox(true)           => """<input checked="" disabled="" type="checkbox">"""
    case Checkbox(false)          => """<input disabled="" type="checkbox">"""
    case HtmlTag(data)            => data
    case other                    => sys.error(s"can't render $other")
  }

  // as the reference implementation writes a URL: percent-encoded, but what's encoded already stays
  private def encodeUri(uri: String): String = {
    val keep = ";/?:@&=+$,-_.!~*'()#"
    val sb   = new StringBuilder
    var i    = 0
    while (i < uri.length) {
      val c = uri.charAt(i)
      if (c == '%' && i + 2 < uri.length && isHex(uri.charAt(i + 1)) && isHex(uri.charAt(i + 2))) {
        sb.append(uri.substring(i, i + 3)); i += 3
      } else if (c < 128 && (c.isLetterOrDigit || keep.indexOf(c) >= 0)) { sb.append(c); i += 1 }
      else {
        val cp = uri.codePointAt(i)
        new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).foreach(b => sb.append(f"%%${b & 0xff}%02X"))
        i += Character.charCount(cp)
      }
    }
    sb.toString
  }

  private def isHex(c: Char): Boolean = c.isDigit || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  private def inStrong(x: Span): String = x match {
    case Bold(spans) => spans.map(inStrong).mkString
    case other       => span(other)
  }

  private val Void       = Set("hr", "br", "img", "input")
  private val BlockStart = "<(?:ul|ol|pre|blockquote|h[1-6]|hr|table|div)\\b".r

  /** What both sides are compared as: no newlines next to tags, no list start numbers or default alignments, and
    * inside list items no `<p>`, but a `¶` between two paragraphs: tight and loose items come out the same.
    */
  def normalize(html: String): String = {
    val flat = html
      .replaceAll("\n(?=<)", "")
      .replaceAll("(?<=>)\n", "")
      .replaceAll(" start=\"\\d+\"", "")
      .replace(" align=\"left\"", "")
      .replaceAll(" title=\"[^\"]*\"", "")
    val out   = new StringBuilder
    val stack = mutable.Stack.empty[String]
    "<[^>]*>|[^<]+".r.findAllIn(flat).foreach { token =>
      if (token.startsWith("</")) {
        val name = token.drop(2).takeWhile(_.isLetterOrDigit).toLowerCase
        if (name == "p" && stack.headOption.contains("p-in-li")) { stack.pop(); out.append('¶') }
        else {
          if (stack.headOption.contains(name)) stack.pop()
          out.append(token)
        }
      } else if (token.startsWith("<") && token.length > 1 && token.charAt(1).isLetter) {
        val name = token.drop(1).takeWhile(_.isLetterOrDigit).toLowerCase
        if (name == "p" && stack.headOption.contains("li")) stack.push("p-in-li")
        else {
          if (!Void(name) && !token.endsWith("/>")) stack.push(name)
          out.append(token)
        }
      } else out.append(token)
    }
    // a ¶ only counts between paragraphs
    out.toString.replaceAll("¶(?=</li>|$)", "").replaceAll("¶(?=" + BlockStart.regex + ")", "")
  }
}
