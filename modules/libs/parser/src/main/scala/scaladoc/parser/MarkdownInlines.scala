package scaladoc.parser

import scaladoc.Markup._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/** Markdown's inline markup, for [[ParseMarkup]], the parser of every syntax: CommonMark's, with GFM's strikethrough (`~~`, as flexmark, which Scala 3
  * uses, has it) and extended autolinks, and scaladoc's `[[target text]]` links.
  *
  * A recursive descent parser like [[ParseMarkup]]: `spansUntil` reads markup up to a closing marker, and markup that
  * can have markup inside, like emphasis or a link's text, is tried with `tryNested`: if its closing marker is in the
  * text, it's that markup, otherwise its opening marker is text. CommonMark's flanking rules say which `*`s and `_`s
  * can open and close. A closer is only looked for where one can be, which keeps parsing linear. Javadoc's inline
  * tags are the ones every syntax has, `ParseMarkup.javadocTagAt`.
  *
  * @param refs  the document's link reference definitions, by normalized label
  * @param notes the labels of the document's footnotes, normalized
  */
private[parser] final class MarkdownInlines private (
  text: String,
  refs: collection.Map[String, MarkdownInlines.Ref],
  notes: collection.Set[String]) {
  import MarkdownInlines._

  private var pos = 0
  private val len = text.length

  private def at(x: String, i: Int): Boolean = text.startsWith(x, i)

  private def charAt(i: Int): Char = if (i >= 0 && i < len) text.charAt(i) else '\n'

  def run(): List[Span] = spansUntil(None, 0, Nil, inBrackets = false, inLink = false, depth = 0)._1

  // ---- what's at a position

  // the run of `*`, `_` or `~` around `i`: whether it can open, and whether it can close, as CommonMark's flanking
  // rules have it
  private def flankingAt(i: Int): (Boolean, Boolean) = {
    val c     = text.charAt(i)
    var start = i
    while (start > 0 && text.charAt(start - 1) == c) start -= 1
    var end = i
    while (end < len && text.charAt(end) == c) end += 1
    val before           = charAt(start - 1)
    val after            = charAt(end)
    val afterWhitespace  = isUnicodeWhitespace(after)
    val afterPunct       = isPunctuation(after)
    val beforeWhitespace = isUnicodeWhitespace(before)
    val beforePunct      = isPunctuation(before)
    val left             = !afterWhitespace && (!afterPunct || beforeWhitespace || beforePunct)
    val right            = !beforeWhitespace && (!beforePunct || afterWhitespace || afterPunct)
    if (c == '_') (left && (!right || beforePunct), right && (!left || afterPunct)) else (left, right)
  }

  // whether `closer` closes what's open at `i`: a `]`, or a run of emphasis markers that can close. As CommonMark
  // has it, when one of the runs can both open and close, their lengths may not add up to a multiple of 3, unless both
  // are one.
  private def closesAt(closer: Closer, i: Int): Boolean =
    at(closer.marker, i) && (closer.marker == "]" || {
      val (opens, closes) = flankingAt(i)
      val run             = runLength(i)
      closes && (closer.marker.charAt(0) match {
        case '~' => run == 2
        case _ =>
          !((opens || closer.openerCanClose) && (closer.openerRun + run) % 3 == 0 &&
            !(closer.openerRun % 3 == 0 && run % 3 == 0))
      })
    })

  // whether a closer for `closer` can be at `from` or after it: where the last `]`, or the last run of markers that can
  // close, is. Without one, what `closer` would close isn't looked for, which keeps parsing linear.
  private def closerAfter(closer: Closer, from: Int): Boolean =
    if (closer.marker == "]") lastBracket >= from
    else lastClosingRun.getOrElse(closer.marker, -1) >= from

  private lazy val lastBracket = text.lastIndexOf(']')

  // by marker, `*`, `**` and the like, where the last run of markers that can close it starts
  private lazy val lastClosingRun: Map[String, Int] = {
    val out = mutable.Map.empty[String, Int]
    var i   = 0
    while (i < len) {
      val c = text.charAt(i)
      if (c == '\\') i += 2
      else if (c == '*' || c == '_' || c == '~') {
        val end = runEnd(text, i, c)
        if (flankingAt(i)._2) {
          if (c != '~') out(c.toString) = i
          if (end - i >= 2 && (c != '~' || end - i == 2)) out(c.toString * 2) = i
        }
        i = end
      } else i += 1
    }
    out.toMap
  }

  private def runLength(i: Int): Int = {
    val c = text.charAt(i)
    var start = i
    while (start > 0 && text.charAt(start - 1) == c) start -= 1
    var end = i
    while (end < len && text.charAt(end) == c) end += 1
    end - start
  }

  // a code span: from a run of backticks to the next run as long; the end of the code
  private def codeSpanAt(i: Int): Option[(String, Int)] = {
    val open = runEnd(text, i, '`')
    val n    = open - i
    if (lastTickRun.getOrElse(n, -1) < open) None
    else {
      var j      = text.indexOf('`', open)
      var closer = -1
      while (closer < 0) {
        val end = runEnd(text, j, '`')
        if (end - j == n) closer = j else j = text.indexOf('`', end)
      }
      // as Scala 3's flexmark has it (CommonMark before 0.29): without the whitespace around it, and a run of it inside,
      // line ends included, is one space
      Some((collapseWhitespace(text.substring(open, closer)), closer + n))
    }
  }

  // the start of the last run of backticks of each length: when there's none of a length after a run, it has no closer
  private lazy val lastTickRun: Map[Int, Int] = {
    val out = mutable.Map.empty[Int, Int]
    var i   = text.indexOf('`')
    while (i >= 0) {
      val end = runEnd(text, i, '`')
      out(end - i) = i
      i = text.indexOf('`', end)
    }
    out.toMap
  }

  // `[[target text]]`: up to the first bracket after `[[`, which has to be the `]]`; not inside more brackets
  private def wikiLinkAt(i: Int): Option[(Span, Int)] =
    if (!at("[[", i) || (i > 0 && text.charAt(i - 1) == '[')) None
    else {
      var end = i + 2
      while (end < len && text.charAt(end) != '[' && text.charAt(end) != ']') end += 1
      val body = if (at("]]", end) && !at("]]]", end)) text.substring(i + 2, end).trim else ""
      if (body.isEmpty) None
      else {
        val (target, label) = splitWikiLink(body)
        Some((Link(target, if (label.isEmpty) Nil else List(PlainText(label))), end + 2))
      }
    }

  // `[^label]`, a reference to a footnote, if the document has one with that label: where it ends
  private def footnoteReferenceAt(i: Int): Option[(Span, Int)] =
    if (!at("[^", i)) None
    else {
      var end = i + 2
      while (end < len && text.charAt(end) != ']' && text.charAt(end) != '[' && !text.charAt(end).isWhitespace) end += 1
      val label = text.substring(i + 2, end)
      if (end >= len || text.charAt(end) != ']' || label.isEmpty || !notes(normalizeLabel(s"[$label]"))) None
      else Some((FootnoteReference(label), end + 1))
    }

  // `<scheme:...>` or `<a@b.c>`: the link, and where it ends
  private def autolinkAt(i: Int): Option[(Span, Int)] =
    uriAutolinkAt(text, i).map(end => (Link(text.substring(i + 1, end - 1), text.substring(i + 1, end - 1)), end))
      .orElse(emailAutolinkAt(text, i).map { end =>
        val address = text.substring(i + 1, end - 1)
        (Link("mailto:" + address, address), end)
      })

  // after a link's text: `(destination "title")`, `[label]`, `[]`, or nothing (with the text as the label)
  private def linkTargetAt(i: Int, label: String): Option[(String, Int)] =
    inlineDestinationAt(i).orElse {
      linkLabelAt(text, i) match {
        case Some(end) if end - i > 2 => refs.get(normalizeLabel(text.substring(i, end))).map(r => (r.destination, end))
        case _ if text.startsWith("[]", i) => refs.get(normalizeLabel(label)).map(r => (r.destination, i + 2))
        case _                        => refs.get(normalizeLabel(label)).map(r => (r.destination, i))
      }
    }

  // `(destination "title")`, with spaces and one line end around them
  private def inlineDestinationAt(i: Int): Option[(String, Int)] =
    if (charAt(i) != '(' || i >= len) None
    else {
      val start = spacesEnd(text, i + 1)
      destinationAt(text, start).flatMap { case (dest, destEnd) =>
        val afterDest = spacesEnd(text, destEnd)
        val afterTitle =
          if (afterDest > destEnd && afterDest < len) titleAt(text, afterDest).map(t => spacesEnd(text, t._2)).getOrElse(afterDest)
          else afterDest
        if (charAt(afterTitle) == ')' && afterTitle < len) Some((dest, afterTitle + 1)) else None
      }
    }

  // ---- markup

  // inline markup up to `stop` (left for the caller) or the end of the text; `true` if it got to `stop`. `stop` closes
  // from `closeFrom` on, after the run of markers that opened it. `outer` are the closers the enclosing markup waits
  // for: when one of them comes first, this markup isn't any. In a link's text, URLs aren't links.
  private def spansUntil(
    stop: Option[Closer],
    closeFrom: Int,
    outer: List[Closer],
    inBrackets: Boolean,
    inLink: Boolean,
    depth: Int): (List[Span], Boolean) = {
    val spans = ListBuffer.empty[Span]
    val buf   = new StringBuilder

    def flush(): Unit = if (buf.nonEmpty) {
      spans ++= (if (inLink) List(PlainText(buf.toString)) else autolinked(buf.toString))
      buf.setLength(0)
    }

    // the markup up to `closer`, from `open` on, if `closer` is in the text: its spans, with `pos` at the closer
    def nested(open: Int, closeFrom: Int, closer: Closer, brackets: Boolean): Option[List[Span]] =
      if (depth >= MaxNesting || !closerAfter(closer, closeFrom)) None
      else {
        val from      = pos
        // a link's text is on its own: links bind tighter than emphasis. Of the enclosing closers, the innermost of each
        // marker: the one that would close first
        val enclosing =
          if (brackets) Nil else stop.fold(outer)(s => s :: outer.filterNot(_.marker == s.marker))
        pos = open
        val (inner, closed) =
          spansUntil(Some(closer.copy(closeFrom = closeFrom)), closeFrom, enclosing, inBrackets || brackets, inLink || brackets, depth + 1)
        if (closed) Some(inner) else { pos = from; None }
      }

    // emphasis that has markup inside, if its closing marker is in the text
    def tryNested(open: Int, closeFrom: Int, closer: Closer)(make: List[Span] => Span): Boolean =
      nested(open, closeFrom, closer, brackets = false) match {
        case Some(inner) => flush(); spans += make(inner); pos += closer.marker.length; true
        case None        => false
      }

    // `*`, `_` or `~~`: emphasis, strong emphasis or strikethrough if they close, text otherwise
    def emphasis(): Unit = {
      val c      = text.charAt(pos)
      val end    = runEnd(text, pos, c)
      val run    = end - pos
      val (opens, closes) = flankingAt(pos)
      // as CommonMark, the rule of 3 goes by the whole run
      val single = Closer(c.toString, runLength(pos), closes)
      val double = Closer(c.toString * 2, runLength(pos), closes)
      // what closes it comes after the run that opens it
      val tried =
        opens && (c match {
          case '~' => run == 2 && tryNested(pos + 2, end, double)(Strikethrough(_))
          // `***x***` is emphasis around strong emphasis, `**x**` strong emphasis, `*x*` emphasis
          case _ if run >= 3 && run % 2 == 1 =>
            tryNested(pos + 1, end, single)(Italic(_)) || tryNested(pos + 2, end, double)(Bold(_))
          case _ if run >= 2 =>
            tryNested(pos + 2, end, double)(Bold(_)) || tryNested(pos + 1, end, single)(Italic(_))
          case _ => tryNested(pos + 1, end, single)(Italic(_))
        })
      if (!tried) {
        // the rest of the run may still open
        buf += c
        pos += 1
        if (!opens || c == '~') { buf ++= text.substring(pos, runEnd(text, pos, c)); pos = runEnd(text, pos, c) }
      }
    }

    // `[text]...` or `![text]...`: a link or an image if a target follows its text; the text in brackets otherwise
    def bracket(image: Boolean): Unit = {
      val opener = if (image) "![" else "["
      val open   = pos + opener.length
      nested(open, open, Closer("]", 0, openerCanClose = false), brackets = true) match {
        case Some(inner) =>
          val close = pos // at the `]`
          linkTargetAt(close + 1, text.substring(open - 1, close + 1)) match {
            // a link can't have links in it: then it's text in brackets, with its links
            case Some((target, end)) if image || !inner.exists(hasLink) =>
              flush()
              spans += (if (image) Image(target, inner.map(altText).mkString) else Link(target, inner))
              pos = end
            // not a link: text in brackets, with its markup. Outside a link's text, if a `*`, `_` or `~` in it didn't
            // close anything, its text is read again: brackets that aren't a link don't stop emphasis
            case _ if inBrackets || !inner.exists(looseMarkers) =>
              buf ++= opener
              flush()
              spans ++= inner
              buf += ']'
              pos = close + 1
            case _ =>
              buf ++= opener
              pos = open
          }
        case None =>
          buf ++= opener
          pos = open
      }
    }

    var closed = false
    var done   = false
    while (!done) {
      if (pos >= len) done = true
      else if (pos >= closeFrom && stop.exists(closesAt(_, pos))) { closed = true; done = true }
      else if (outer.exists(o => pos >= o.closeFrom && closesAt(o, pos))) done = true
      else if (inBrackets && text.charAt(pos) == ']' && !stop.exists(_.marker == "]")) done = true
      else
        text.charAt(pos) match {
          case '\\' =>
            if (charAt(pos + 1) == '\n' && pos + 1 < len) { flush(); spans += LineBreak; pos = runEnd(text, pos + 2, ' ') }
            else if (pos + 1 < len && Escapable.indexOf(text.charAt(pos + 1)) >= 0) { buf += text.charAt(pos + 1); pos += 2 }
            else { buf += '\\'; pos += 1 }
          case '`' =>
            codeSpanAt(pos) match {
              case Some((code, end)) => flush(); spans += Monospace(code); pos = end
              case None              => val end = runEnd(text, pos, '`'); buf ++= text.substring(pos, end); pos = end
            }
          case '*' | '_' | '~' => emphasis()
          case '[' if footnoteReferenceAt(pos).isDefined =>
            val (reference, end) = footnoteReferenceAt(pos).get
            flush()
            spans += reference
            pos = end
          case '[' =>
            wikiLinkAt(pos) match {
              case Some((link, end)) => flush(); spans += link; pos = end
              case None              => bracket(image = false)
            }
          case '!' if charAt(pos + 1) == '[' && pos + 1 < len => bracket(image = true)
          case '<' =>
            autolinkAt(pos) match {
              case Some((link, end)) => flush(); spans += link; pos = end
              case None =>
                MarkdownHtml.inlineAt(text, pos) match {
                  case Some(end) => flush(); spans += HtmlTag(text.substring(pos, end)); pos = end
                  case None      => buf += '<'; pos += 1
                }
            }
          // javadoc's inline tags, as in every syntax
          case '{' if at("{@", pos) =>
            ParseMarkup.javadocTagAt(text, pos, _ >= len, MarkdownInlines(_, refs, notes)) match {
              case Some((Left(content), end)) => buf ++= content; pos = end
              case Some((Right(span), end))   => flush(); spans += span; pos = end
              case None                       => buf ++= "{@"; pos += 2
            }
          case '&' =>
            entityAt(text, pos) match {
              case Some(end) => buf ++= decodeEntity(text.substring(pos, end)); pos = end
              case None      => buf += '&'; pos += 1
            }
          case '\n' =>
            // a line end, without the spaces around it; after 2 spaces or more, a hard break
            var spaces = 0
            while (buf.nonEmpty && buf.last == ' ') { buf.setLength(buf.length - 1); spaces += 1 }
            if (spaces >= 2) { flush(); spans += LineBreak } else buf += '\n'
            pos = runEnd(text, pos + 1, ' ')
          case c =>
            buf += c
            pos += 1
        }
    }
    flush()
    (mergeTexts(spans.toList), closed)
  }
}

private[parser] object MarkdownInlines {

  final case class Ref(destination: String, title: String)

  /** What closes markup: its closing marker, the run of markers that opened it, for the rule of 3, and where the run
    * ends, from where it can close.
    */
  private final case class Closer(marker: String, openerRun: Int, openerCanClose: Boolean, closeFrom: Int = 0)

  /** How deep inline markup nests at most: what would nest deeper is text. */
  final val MaxNesting = 100

  def apply(text: String, refs: collection.Map[String, Ref], notes: collection.Set[String] = Set.empty): List[Span] =
    new MarkdownInlines(text, refs, notes).run()

  /** The link reference definitions `s` starts with, `[label]: destination "title"`, each on lines of its own: they
    * go to `refs` (the first one for a label wins), and what follows them is returned.
    */
  def takeReferences(s: String, refs: mutable.Map[String, Ref]): String = {
    var i    = 0
    var more = true
    while (more) {
      referenceAt(s, i) match {
        case Some((label, ref, end)) =>
          if (!refs.contains(label)) refs(label) = ref
          i = end
        case None => more = false
      }
    }
    s.substring(i)
  }

  // `[label]: destination "title"` and the end of its line: the normalized label, the definition, and where it ends
  private def referenceAt(s: String, i: Int): Option[(String, Ref, Int)] =
    linkLabelAt(s, i).filter(end => end < s.length && s.charAt(end) == ':').flatMap { labelEnd =>
      val label = normalizeLabel(s.substring(i, labelEnd))
      destinationAt(s, spacesEnd(s, labelEnd + 1)).filter(_ => label.nonEmpty).flatMap { case (dest, destEnd) =>
        val beforeTitle = spacesEnd(s, destEnd)
        // a title needs whitespace before it, and nothing may follow on its line; without it, the line has to end
        val titled = if (beforeTitle > destEnd) titleAt(s, beforeTitle) else None
        titled.flatMap { case (title, end) => lineEndAt(s, end).map(e => (label, Ref(dest, title), e)) }
          .orElse(lineEndAt(s, destEnd).map(e => (label, Ref(dest, ""), e)))
      }
    }

  /** The spec's label normalization: no brackets, whitespace collapsed, case folded. */
  def normalizeLabel(label: String): String = {
    val sb    = new StringBuilder
    var space = false
    label.substring(1, label.length - 1).trim.foreach { c =>
      if (c == ' ' || c == '\t' || c == '\r' || c == '\n') space = true
      else {
        if (space) sb.append(' ')
        space = false
        sb.append(c)
      }
    }
    sb.toString.toLowerCase.toUpperCase
  }

  /** The target, and after the first space that isn't escaped (with a `\` that isn't escaped itself), the text: as
    * Scala 3 splits a `[[...]]` link.
    */
  def splitWikiLink(body: String): (String, String) = {
    def escaped(i: Int) = i >= 1 && body.charAt(i - 1) == '\\' && !(i >= 2 && body.charAt(i - 2) == '\\')
    body.indices.find(i => body.charAt(i) == ' ' && !escaped(i)) match {
      case Some(i) => (body.substring(0, i), body.substring(i + 1).trim)
      case None    => (body, "")
    }
  }

  // ---- what's at a position in a text

  /** A link label, `[...]` with no unescaped brackets in it and at most 999 characters: where it ends. */
  def linkLabelAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || s.charAt(i) != '[') None
    else {
      var j      = i + 1
      var result = Option.empty[Int]
      var more   = true
      while (more && j < s.length && j - i <= 1000) {
        s.charAt(j) match {
          case ']'                      => result = Some(j + 1); more = false
          case '['                      => more = false
          case '\\' if j + 1 < s.length => j += 2
          case _                        => j += 1
        }
      }
      result.filter(end => s.substring(i + 1, end - 1).exists(c => !isWhitespace(c)))
    }

  /** A link destination: `<...>`, or a run without spaces or control characters, with balanced parentheses (at most
    * 32 open, as cmark has it). Its text, unescaped, and where it ends.
    */
  def destinationAt(s: String, i: Int): Option[(String, Int)] =
    if (i < s.length && s.charAt(i) == '<') {
      var j   = i + 1
      var end = Option.empty[Int]
      while (end.isEmpty && j < s.length && s.charAt(j) != '<' && s.charAt(j) != '\n') {
        if (s.charAt(j) == '>') end = Some(j + 1)
        else if (s.charAt(j) == '\\' && j + 1 < s.length && s.charAt(j + 1) != '\n') j += 2
        else j += 1
      }
      end.map(e => (unescape(s.substring(i + 1, e - 1)), e))
    } else {
      var j      = i
      var parens = 0
      var done   = false
      while (!done && j < s.length) {
        val c = s.charAt(j)
        if (c == '\\' && j + 1 < s.length && Escapable.indexOf(s.charAt(j + 1)) >= 0) j += 2
        else if (c == '(') { parens += 1; j += 1; if (parens > 32) done = true }
        else if (c == ')') { if (parens == 0) done = true else { parens -= 1; j += 1 } }
        else if (isWhitespace(c) || c < ' ') done = true
        else j += 1
      }
      if (parens != 0 || (j == i && (j >= s.length || s.charAt(j) != ')'))) None
      else Some((unescape(s.substring(i, j)), j))
    }

  /** A link title, `"..."`, `'...'` or `(...)`, with escapes: its text, unescaped, and where it ends. */
  def titleAt(s: String, i: Int): Option[(String, Int)] =
    if (i >= s.length || "\"'(".indexOf(s.charAt(i)) < 0) None
    else {
      val close  = if (s.charAt(i) == '(') ')' else s.charAt(i)
      var j      = i + 1
      var result = Option.empty[(String, Int)]
      var more   = true
      while (more && j < s.length) {
        val c = s.charAt(j)
        if (c == '\\' && j + 1 < s.length) j += 2
        else if (c == close) { result = Some((unescape(s.substring(i + 1, j)), j + 1)); more = false }
        else if (close == ')' && c == '(') more = false
        else j += 1
      }
      result
    }

  /** `<scheme:...>`: where it ends. */
  def uriAutolinkAt(s: String, i: Int): Option[Int] =
    if (i + 1 >= s.length || s.charAt(i) != '<' || !isAsciiLetter(s.charAt(i + 1))) None
    else {
      var j = i + 2
      while (j < s.length && (isAsciiAlnum(s.charAt(j)) || ".+-".indexOf(s.charAt(j)) >= 0)) j += 1
      val scheme = j - i - 1
      if (scheme < 2 || scheme > 32 || j >= s.length || s.charAt(j) != ':') None
      else {
        j += 1
        while (j < s.length && s.charAt(j) > ' ' && s.charAt(j) != '<' && s.charAt(j) != '>') j += 1
        if (j < s.length && s.charAt(j) == '>') Some(j + 1) else None
      }
    }

  /** `<a@b.c>`: where it ends. */
  def emailAutolinkAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || s.charAt(i) != '<') None
    else {
      var j = i + 1
      while (j < s.length && (isAsciiAlnum(s.charAt(j)) || "!#$%&'*+/=?^_`{|}~-.".indexOf(s.charAt(j)) >= 0)) j += 1
      if (j == i + 1 || j >= s.length || s.charAt(j) != '@') None
      else {
        // labels of letters, digits and `-`, not at either end, of at most 63, separated by `.`
        var k     = j + 1
        var valid = true
        var first = true
        while (valid && (first || (k < s.length && s.charAt(k) == '.'))) {
          if (!first) k += 1
          first = false
          val start = k
          while (k < s.length && (isAsciiAlnum(s.charAt(k)) || s.charAt(k) == '-')) k += 1
          valid = k - start >= 1 && k - start <= 63 && s.charAt(start) != '-' && s.charAt(k - 1) != '-'
        }
        if (valid && k < s.length && s.charAt(k) == '>') Some(k + 1) else None
      }
    }

  /** An entity, `&name;`, `&#123;` or `&#x7B;`: where it ends. */
  def entityAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || s.charAt(i) != '&') None
    else {
      def number(from: Int, max: Int, digit: Char => Boolean): Option[Int] = {
        var j = from
        while (j < s.length && j - from < max && digit(s.charAt(j))) j += 1
        if (j == from || j >= s.length || s.charAt(j) != ';') None else Some(j + 1)
      }
      if (s.startsWith("&#x", i) || s.startsWith("&#X", i)) number(i + 3, 6, isHexDigit)
      else if (s.startsWith("&#", i)) number(i + 2, 7, c => c >= '0' && c <= '9')
      else if (i + 1 < s.length && isAsciiLetter(s.charAt(i + 1))) {
        var j = i + 2
        while (j < s.length && j - i - 1 < 32 && isAsciiAlnum(s.charAt(j))) j += 1
        if (j - i - 1 < 2 || j >= s.length || s.charAt(j) != ';') None else Some(j + 1)
      } else None
    }

  // spaces and tabs from `i`, with at most one line end among them: where they end
  private def spacesEnd(s: String, i: Int): Int = {
    var j = i
    while (j < s.length && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j += 1
    if (j < s.length && s.charAt(j) == '\n') {
      j += 1
      while (j < s.length && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j += 1
    }
    j
  }

  // nothing but spaces up to the line's end: past it
  private def lineEndAt(s: String, i: Int): Option[Int] = {
    var j = i
    while (j < s.length && (s.charAt(j) == ' ' || s.charAt(j) == '\t')) j += 1
    if (j >= s.length) Some(j) else if (s.charAt(j) == '\n') Some(j + 1) else None
  }

  // ---- GFM's extended autolinks: `www.`, `http(s)://` and `ftp://` URLs, and email addresses, in text

  private def autolinked(s: String): List[Span] = {
    val out = ListBuffer.empty[Span]
    var at  = 0
    var i   = 0
    while (i < s.length) {
      val boundary = i == 0 || " \t\n*_~(".indexOf(s.charAt(i - 1)) >= 0
      extendedAutolinkAt(s, i, boundary) match {
        case Some((end, target)) =>
          if (i > at) out += PlainText(s.substring(at, i))
          out += Link(target, List(PlainText(s.substring(i, end))))
          i = end
          at = end
        case None => i += 1
      }
    }
    if (at < s.length) out += PlainText(s.substring(at))
    out.toList
  }

  // an extended autolink at `i`: where it ends, and its target
  private def extendedAutolinkAt(s: String, i: Int, boundary: Boolean): Option[(Int, String)] =
    if (boundary && (s.startsWith("www.", i) || s.startsWith("http://", i) || s.startsWith("https://", i) ||
        s.startsWith("ftp://", i))) {
      val domainStart = if (s.startsWith("www.", i)) i else s.indexOf("://", i) + 3
      domainAt(s, domainStart).map { domainEnd =>
        var pathEnd = domainEnd
        while (pathEnd < s.length && !isWhitespace(s.charAt(pathEnd)) && s.charAt(pathEnd) != '<') pathEnd += 1
        val end = withoutTrailing(s, i, pathEnd)
        val url = s.substring(i, end)
        (end, if (url.startsWith("www.")) "http://" + url else url)
      }
    } else if (isAsciiAlnum(s.charAt(i)) || ".+-_".indexOf(s.charAt(i)) >= 0) {
      // an email address starts after something that can't be in one
      if (i > 0 && (isAsciiAlnum(s.charAt(i - 1)) || ".+-_@".indexOf(s.charAt(i - 1)) >= 0)) None
      else emailAt(s, i).map(end => (end, "mailto:" + s.substring(i, end)))
    } else None

  // GFM's domain: segments of letters, digits, `_` and `-`, separated by `.`, at least two, with no `_` in the last two
  private def domainAt(s: String, i: Int): Option[Int] = {
    var j        = i
    var segments = List.empty[(Int, Int)]
    var more     = true
    while (more) {
      val start = j
      while (j < s.length && (isAsciiAlnum(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '-')) j += 1
      if (j == start) more = false
      else {
        segments = (start, j) :: segments
        if (j + 1 < s.length && s.charAt(j) == '.' && {
              val c = s.charAt(j + 1); isAsciiAlnum(c) || c == '_' || c == '-'
            }) j += 1
        else more = false
      }
    }
    if (segments.length < 2 || segments.take(2).exists { case (a, b) => s.substring(a, b).contains('_') }) None
    else Some(segments.head._2)
  }

  // GFM's email address, `a.b+c@d.e`: where it ends, a trailing `.` not included
  private def emailAt(s: String, i: Int): Option[Int] = {
    var j = i
    while (j < s.length && (isAsciiAlnum(s.charAt(j)) || ".+_-".indexOf(s.charAt(j)) >= 0)) j += 1
    if (j == i || j >= s.length || s.charAt(j) != '@') None
    else {
      var k      = j + 1
      var labels = 0
      var more   = true
      while (more) {
        val start = k
        while (k < s.length && (isAsciiAlnum(s.charAt(k)) || s.charAt(k) == '_' || s.charAt(k) == '-')) k += 1
        if (k == start) { more = false; if (labels > 0) k -= 1 }
        else {
          labels += 1
          if (k < s.length && s.charAt(k) == '.') k += 1 else more = false
        }
      }
      val last = s.charAt(k - 1)
      if (labels < 2 || last == '-' || last == '_') None else Some(k)
    }
  }

  // an extended autolink's trailing punctuation isn't part of it, nor an unmatched `)`, nor something like `&amp;`
  private def withoutTrailing(s: String, start: Int, end0: Int): Int = {
    var end    = end0
    var opened = 0
    var closed = 0
    var k      = start
    while (k < end) { if (s.charAt(k) == '(') opened += 1 else if (s.charAt(k) == ')') closed += 1; k += 1 }
    var changed = true
    while (changed && end > start) {
      changed = false
      val c = s.charAt(end - 1)
      if ("?!.,:*_~".indexOf(c) >= 0) { end -= 1; changed = true }
      else if (c == ')' && closed > opened) { end -= 1; closed -= 1; changed = true }
      else if (c == ';') {
        var j = end - 1
        while (j > start && isAsciiAlnum(s.charAt(j - 1))) j -= 1
        if (j < end - 1 && j > start && s.charAt(j - 1) == '&') { end = j - 1; changed = true }
      }
    }
    end
  }

  // ---- characters and text

  private val Escapable = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

  private def isAsciiLetter(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

  private def isAsciiAlnum(c: Char): Boolean = isAsciiLetter(c) || (c >= '0' && c <= '9')

  private def isHexDigit(c: Char): Boolean = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  private def isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r'

  private def isUnicodeWhitespace(c: Char): Boolean =
    c == '\t' || c == '\n' || c == '\f' || c == '\r' || Character.getType(c) == Character.SPACE_SEPARATOR

  private def isPunctuation(c: Char): Boolean = Escapable.indexOf(c) >= 0 || (Character.getType(c) match {
    case Character.CONNECTOR_PUNCTUATION | Character.DASH_PUNCTUATION | Character.START_PUNCTUATION |
        Character.END_PUNCTUATION | Character.INITIAL_QUOTE_PUNCTUATION | Character.FINAL_QUOTE_PUNCTUATION |
        Character.OTHER_PUNCTUATION => true
    case _ => false
  })

  private def runEnd(s: String, i: Int, c: Char): Int = {
    var j = i
    while (j < s.length && s.charAt(j) == c) j += 1
    j
  }

  // text next to text is one text: `[a]` that isn't a link is `PlainText("[a]")`, not three
  private def mergeTexts(spans: List[Span]): List[Span] = {
    val out = ListBuffer.empty[Span]
    val buf = new StringBuilder
    spans.foreach {
      case PlainText(t) => buf ++= t
      case span =>
        if (buf.nonEmpty) { out += PlainText(buf.toString); buf.setLength(0) }
        out += span
    }
    if (buf.nonEmpty) out += PlainText(buf.toString)
    out.toList
  }

  private def collapseWhitespace(s: String): String = {
    val sb    = new StringBuilder
    var space = false
    s.foreach { c =>
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') space = true
      else {
        if (space && sb.nonEmpty) sb += ' '
        space = false
        sb += c
      }
    }
    sb.toString
  }

  private def looseMarkers(s: Span): Boolean = s match {
    case PlainText(t) => t.exists(c => c == '*' || c == '_' || c == '~')
    case _            => false
  }

  private def hasLink(s: Span): Boolean = s match {
    case _: Link     => true
    case x: HasSpans => x.spans.exists(hasLink)
    case _           => false
  }

  // an image's alternative text: the text of what's in its brackets, without markup
  private def altText(s: Span): String = s match {
    case PlainText(t)   => t
    case Monospace(t)   => t
    case x: HasSpans    => x.spans.map(altText).mkString
    case Link(_, title) => title.map(altText).mkString
    case Image(_, alt)  => alt
    case LineBreak      => "\n"
    case _              => ""
  }

  /** Backslash escapes and entities, as in link destinations and titles, and code fences' info strings. */
  def unescape(x: String): String =
    if (x.indexOf('\\') < 0 && x.indexOf('&') < 0) x
    else {
      val sb = new StringBuilder
      var i  = 0
      while (i < x.length) {
        val c = x.charAt(i)
        if (c == '\\' && i + 1 < x.length && Escapable.indexOf(x.charAt(i + 1)) >= 0) { sb.append(x.charAt(i + 1)); i += 2 }
        else if (c == '&') {
          entityAt(x, i) match {
            case Some(end) => sb.append(decodeEntity(x.substring(i, end))); i = end
            case None      => sb.append(c); i += 1
          }
        } else { sb.append(c); i += 1 }
      }
      sb.toString
    }

  /** `&amp;`, `&#38;` or `&#x26;`: what it stands for. An unknown name stays as it is, and a number that's not a
    * character is U+FFFD.
    */
  def decodeEntity(e: String): String = {
    val body = e.substring(1, e.length - 1)
    if (body.startsWith("#")) {
      val n =
        try {
          if (body.length > 1 && (body.charAt(1) == 'x' || body.charAt(1) == 'X')) Integer.parseInt(body.substring(2), 16)
          else Integer.parseInt(body.substring(1))
        } catch { case _: NumberFormatException => 0 }
      if (n == 0 || n > 0x10ffff || (n >= 0xd800 && n <= 0xdfff)) "�" else new String(Character.toChars(n))
    } else Entities.named.getOrElse(body, e)
  }
}

private[parser] object Entities {

  // HTML 4's entities, the ones doc comments use; the full HTML 5 table has over 2000
  private val latin1 =
    ("nbsp iexcl cent pound curren yen brvbar sect uml copy ordf laquo not shy reg macr deg plusmn sup2 sup3 acute " +
      "micro para middot cedil sup1 ordm raquo frac14 frac12 frac34 iquest Agrave Aacute Acirc Atilde Auml Aring " +
      "AElig Ccedil Egrave Eacute Ecirc Euml Igrave Iacute Icirc Iuml ETH Ntilde Ograve Oacute Ocirc Otilde Ouml " +
      "times Oslash Ugrave Uacute Ucirc Uuml Yacute THORN szlig agrave aacute acirc atilde auml aring aelig ccedil " +
      "egrave eacute ecirc euml igrave iacute icirc iuml eth ntilde ograve oacute ocirc otilde ouml divide oslash " +
      "ugrave uacute ucirc uuml yacute thorn yuml").split(' ').zipWithIndex.map { case (n, i) => n -> (160 + i) }

  private val greekUpper = "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda Mu Nu Xi Omicron Pi Rho"
    .split(' ').zipWithIndex.map { case (n, i) => n -> (913 + i) } ++
    "Sigma Tau Upsilon Phi Chi Psi Omega".split(' ').zipWithIndex.map { case (n, i) => n -> (931 + i) }

  private val greekLower =
    ("alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigmaf sigma tau " +
      "upsilon phi chi psi omega").split(' ').zipWithIndex.map { case (n, i) => n -> (945 + i) }

  private val others = Seq(
    "quot" -> 34, "amp" -> 38, "apos" -> 39, "lt" -> 60, "gt" -> 62, "OElig" -> 338, "oelig" -> 339,
    "Scaron" -> 352, "scaron" -> 353, "Yuml" -> 376, "fnof" -> 402, "circ" -> 710, "tilde" -> 732, "thetasym" -> 977,
    "upsih" -> 978, "piv" -> 982, "ensp" -> 8194, "emsp" -> 8195, "thinsp" -> 8201, "zwnj" -> 8204, "zwj" -> 8205,
    "lrm" -> 8206, "rlm" -> 8207, "ndash" -> 8211, "mdash" -> 8212, "lsquo" -> 8216, "rsquo" -> 8217,
    "sbquo" -> 8218, "ldquo" -> 8220, "rdquo" -> 8221, "bdquo" -> 8222, "dagger" -> 8224, "Dagger" -> 8225,
    "bull" -> 8226, "hellip" -> 8230, "permil" -> 8240, "prime" -> 8242, "Prime" -> 8243, "lsaquo" -> 8249,
    "rsaquo" -> 8250, "oline" -> 8254, "frasl" -> 8260, "euro" -> 8364, "image" -> 8465, "weierp" -> 8472,
    "real" -> 8476, "trade" -> 8482, "alefsym" -> 8501, "larr" -> 8592, "uarr" -> 8593, "rarr" -> 8594,
    "darr" -> 8595, "harr" -> 8596, "crarr" -> 8629, "lArr" -> 8656, "uArr" -> 8657, "rArr" -> 8658,
    "dArr" -> 8659, "hArr" -> 8660, "forall" -> 8704, "part" -> 8706, "exist" -> 8707, "empty" -> 8709,
    "nabla" -> 8711, "isin" -> 8712, "notin" -> 8713, "ni" -> 8715, "prod" -> 8719, "sum" -> 8721,
    "minus" -> 8722, "lowast" -> 8727, "radic" -> 8730, "prop" -> 8733, "infin" -> 8734, "ang" -> 8736,
    "and" -> 8743, "or" -> 8744, "cap" -> 8745, "cup" -> 8746, "int" -> 8747, "there4" -> 8756, "sim" -> 8764,
    "cong" -> 8773, "asymp" -> 8776, "ne" -> 8800, "equiv" -> 8801, "le" -> 8804, "ge" -> 8805, "sub" -> 8834,
    "sup" -> 8835, "nsub" -> 8836, "sube" -> 8838, "supe" -> 8839, "oplus" -> 8853, "otimes" -> 8855,
    "perp" -> 8869, "sdot" -> 8901, "lceil" -> 8968, "rceil" -> 8969, "lfloor" -> 8970, "rfloor" -> 8971,
    "lang" -> 9001, "rang" -> 9002, "loz" -> 9674, "spades" -> 9824, "clubs" -> 9827, "hearts" -> 9829,
    "diams" -> 9830,
    // a few HTML 5 ones the CommonMark spec's examples use
    "Dcaron" -> 270, "HilbertSpace" -> 8459, "DifferentialD" -> 8518, "ClockwiseContourIntegral" -> 8754
  )

  val named: Map[String, String] =
    (latin1 ++ greekUpper ++ greekLower ++ others).map { case (n, c) => n -> new String(Character.toChars(c)) }.toMap +
      ("ngE" -> "\u2267\u0338")
}
