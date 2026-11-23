package scaladoc.parser

import scaladoc.{CommentSyntax, Markup}

/** What the parser needs to know about a [[scaladoc.CommentSyntax]]: how a tag body's markup parses, and where code
  * starts and ends, in which nothing is a tag, so that code samples can have annotations like `@main`.
  */
private[scaladoc] trait MarkupSyntax {

  /** The markup of a tag body (or of the description), as a document. */
  def document(text: String): Markup.Document

  /** If code starts at `i` of `chars`, what ends it. */
  def codeStartsAt(chars: Array[Char], i: Int): Option[MarkupSyntax.CodeEnd]
}

private[scaladoc] object MarkupSyntax {

  /** The end of code that started earlier. */
  trait CodeEnd {

    /** Whether the code ends at `i` of `chars`. */
    def endsAt(chars: Array[Char], i: Int): Boolean
  }

  def apply(syntax: CommentSyntax): MarkupSyntax = syntax match {
    case CommentSyntax.Wiki     => Wiki
    case CommentSyntax.Markdown => Markdown
  }

  /** scaladoc's wiki syntax: code is `{{{ ... }}}` or `<pre>...</pre>`. */
  object Wiki extends MarkupSyntax {

    def document(text: String): Markup.Document = ParseMarkup(text)

    def codeStartsAt(chars: Array[Char], i: Int): Option[CodeEnd] =
      if (at(chars, i, "{{{", ignoreCase = false)) Some(Marker("}}}", ignoreCase = false))
      else if (at(chars, i, "<pre", ignoreCase = true)) Some(Marker("</pre>", ignoreCase = true))
      else None
  }

  /** Markdown: code is a fenced block, ```` ``` ```` or `~~~` on lines of their own, or `{{{ ... }}}` or
    * `<pre>...</pre>`, as in wiki.
    */
  object Markdown extends MarkupSyntax {

    def document(text: String): Markup.Document = ParseMarkdown(text)

    def codeStartsAt(chars: Array[Char], i: Int): Option[CodeEnd] =
      fenceAt(chars, i).flatMap { case (fenceChar, length) =>
        // without a closing fence, it's not taken for code here: a forgotten one would hide every tag after it
        val end = Fence(fenceChar, length)
        val next = chars.indexOf('\n', i)
        if (next >= 0 && (next + 1 until chars.length).exists(end.endsAt(chars, _))) Some(end) else None
      }.orElse(Wiki.codeStartsAt(chars, i))
  }

  // a code fence that opens at `i`: at most 3 spaces into its line, 3 or more backticks or tildes
  private def fenceAt(chars: Array[Char], i: Int): Option[(Char, Int)] =
    if (i >= chars.length || (chars(i) != '`' && chars(i) != '~') || !lineStartWithin3(chars, i)) None
    else {
      val c   = chars(i)
      val run = chars.iterator.drop(i).takeWhile(_ == c).size
      if (run >= 3) Some((c, run)) else None
    }

  private def lineStartWithin3(chars: Array[Char], i: Int): Boolean = {
    var j = i - 1
    while (j >= 0 && chars(j) == ' ') j -= 1
    (j < 0 || chars(j) == '\n') && i - j - 1 <= 3
  }

  /** A closing fence: of the opening fence's character, at least as long, and nothing else on its line. */
  private final case class Fence(fenceChar: Char, length: Int) extends CodeEnd {
    def endsAt(chars: Array[Char], i: Int): Boolean = fenceAt(chars, i).exists { case (c, run) =>
      c == fenceChar && run >= length &&
        chars.iterator.drop(i + run).takeWhile(_ != '\n').forall(x => x == ' ' || x == '\t')
    }
  }

  private final case class Marker(marker: String, ignoreCase: Boolean) extends CodeEnd {
    def endsAt(chars: Array[Char], i: Int): Boolean = at(chars, i, marker, ignoreCase)
  }

  private def at(chars: Array[Char], i: Int, x: String, ignoreCase: Boolean): Boolean =
    chars.length - i >= x.length && x.indices.forall { j =>
      val c = chars(i + j)
      if (ignoreCase) c.toLower == x.charAt(j).toLower else c == x.charAt(j)
    }
}
