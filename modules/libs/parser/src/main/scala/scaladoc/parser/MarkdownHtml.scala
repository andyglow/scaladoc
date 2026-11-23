package scaladoc.parser

/** HTML as Markdown has it, for both kinds of HTML in it: blocks, and inline HTML. Each `xAt(s, i)` says whether `x`
  * is in `s` at `i`, and where it ends.
  */
private[parser] object MarkdownHtml {

  private def isAsciiLetter(c: Char): Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

  private def isAsciiAlnum(c: Char): Boolean = isAsciiLetter(c) || (c >= '0' && c <= '9')

  private def isWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r'

  private def isBlank(s: String, from: Int): Boolean = (from until s.length).forall(i => s.charAt(i) == ' ' || s.charAt(i) == '\t')

  private val BlockTagNames = Set(
    "address", "article", "aside", "base", "basefont", "blockquote", "body", "caption", "center", "col", "colgroup",
    "dd", "details", "dialog", "dir", "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "frame",
    "frameset", "h1", "h2", "h3", "h4", "h5", "h6", "head", "header", "hr", "html", "iframe", "legend", "li", "link",
    "main", "menu", "menuitem", "nav", "noframes", "ol", "optgroup", "option", "p", "param", "section", "source",
    "summary", "table", "tbody", "td", "tfoot", "th", "thead", "title", "tr", "track", "ul")

  private val RawTextTags = List("script", "pre", "style")

  /** Which of the spec's seven kinds of HTML block starts in the line `s` at `i`, 1 to 7. */
  def blockAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || s.charAt(i) != '<') None
    else if (RawTextTags.exists { t =>
               s.regionMatches(true, i + 1, t, 0, t.length) && {
                 val after = i + 1 + t.length
                 after >= s.length || isWhitespace(s.charAt(after)) || s.charAt(after) == '>'
               }
             }) Some(1)
    else if (s.startsWith("<!--", i)) Some(2)
    else if (s.startsWith("<?", i)) Some(3)
    else if (s.startsWith("<!", i) && i + 2 < s.length && s.charAt(i + 2) >= 'A' && s.charAt(i + 2) <= 'Z') Some(4)
    else if (s.startsWith("<![CDATA[", i)) Some(5)
    else {
      val start = if (s.startsWith("</", i)) i + 2 else i + 1
      val block = tagNameAt(s, start).exists { end =>
        BlockTagNames(s.substring(start, end).toLowerCase) &&
        (end >= s.length || isWhitespace(s.charAt(end)) || s.charAt(end) == '>' || s.startsWith("/>", end))
      }
      if (block) Some(6)
      // a whole tag, and nothing else on the line
      else openTagAt(s, i).orElse(closingTagAt(s, i)).filter(isBlank(s, _)).map(_ => 7)
    }

  /** Whether the line `s` ends an HTML block of `kind` 1 to 5. */
  def blockEnds(kind: Int, s: String): Boolean = kind match {
    case 1 =>
      val lower = s.toLowerCase
      RawTextTags.exists(t => lower.contains(s"</$t>"))
    case 2 => s.contains("-->")
    case 3 => s.contains("?>")
    case 4 => s.contains(">")
    case 5 => s.contains("]]>")
    case _ => false
  }

  private def tagNameAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || !isAsciiLetter(s.charAt(i))) None
    else {
      var j = i + 1
      while (j < s.length && (isAsciiAlnum(s.charAt(j)) || s.charAt(j) == '-')) j += 1
      Some(j)
    }

  private def whitespaceEnd(s: String, i: Int): Int = {
    var j = i
    while (j < s.length && isWhitespace(s.charAt(j))) j += 1
    j
  }

  /** An open tag, `<a href="x">` or `<br/>`. */
  def openTagAt(s: String, i: Int): Option[Int] =
    if (i >= s.length || s.charAt(i) != '<') None
    else
      tagNameAt(s, i + 1).flatMap { nameEnd =>
        // its attributes, each after whitespace
        var j: Option[Int] = Some(nameEnd)
        var more           = true
        while (more && j.isDefined) {
          val ws = whitespaceEnd(s, j.get)
          if (ws > j.get && ws < s.length && attributeNameStart(s.charAt(ws))) j = attributeAt(s, ws)
          else more = false
        }
        j.flatMap { end =>
          val k = whitespaceEnd(s, end)
          if (s.startsWith("/>", k)) Some(k + 2)
          else if (k < s.length && s.charAt(k) == '>') Some(k + 1)
          else None
        }
      }

  private def attributeNameStart(c: Char): Boolean = isAsciiLetter(c) || c == '_' || c == ':'

  // `name`, maybe with `= value`, the value unquoted or in quotes
  private def attributeAt(s: String, i: Int): Option[Int] = {
    var j = i + 1
    while (j < s.length && (isAsciiAlnum(s.charAt(j)) || "_.:-".indexOf(s.charAt(j)) >= 0)) j += 1
    val eq = whitespaceEnd(s, j)
    if (eq >= s.length || s.charAt(eq) != '=') Some(j)
    else {
      val v = whitespaceEnd(s, eq + 1)
      if (v >= s.length) None
      else
        s.charAt(v) match {
          case q @ ('"' | '\'') =>
            val close = s.indexOf(q, v + 1)
            if (close < 0) None else Some(close + 1)
          case _ =>
            var k = v
            while (k < s.length && s.charAt(k) > ' ' && "\"'=<>`".indexOf(s.charAt(k)) < 0) k += 1
            if (k == v) None else Some(k)
        }
    }
  }

  /** A closing tag, `</a>`. */
  def closingTagAt(s: String, i: Int): Option[Int] =
    if (!s.startsWith("</", i)) None
    else
      tagNameAt(s, i + 2).flatMap { nameEnd =>
        val j = whitespaceEnd(s, nameEnd)
        if (j < s.length && s.charAt(j) == '>') Some(j + 1) else None
      }

  /** Inline HTML: a tag, a comment, a processing instruction, a declaration or a CDATA section. */
  def inlineAt(s: String, i: Int): Option[Int] =
    if (i + 1 >= s.length || s.charAt(i) != '<') None
    else
      s.charAt(i + 1) match {
        case '/' => closingTagAt(s, i)
        case '?' => after(s, "?>", i + 2)
        case '!' =>
          if (s.startsWith("<!-->", i)) Some(i + 5)
          else if (s.startsWith("<!--->", i)) Some(i + 6)
          else if (s.startsWith("<!--", i)) after(s, "-->", i + 4)
          else if (s.startsWith("<![CDATA[", i)) after(s, "]]>", i + 9)
          else {
            // a declaration: `<!`, uppercase letters, whitespace, and anything up to a `>`
            var j = i + 2
            while (j < s.length && s.charAt(j) >= 'A' && s.charAt(j) <= 'Z') j += 1
            if (j == i + 2 || j >= s.length || !isWhitespace(s.charAt(j))) None else after(s, ">", j)
          }
        case _ => openTagAt(s, i)
      }

  // where the next `x` from `i` ends
  private def after(s: String, x: String, i: Int): Option[Int] = {
    val k = s.indexOf(x, i)
    if (k < 0) None else Some(k + x.length)
  }
}
