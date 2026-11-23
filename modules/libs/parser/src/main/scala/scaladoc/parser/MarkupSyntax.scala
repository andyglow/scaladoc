package scaladoc.parser

import scaladoc.{CommentSyntax, Markup}

import scala.collection.mutable

/** What the parser needs to know about a [[scaladoc.CommentSyntax]]: how a tag body's markup parses, and where code
  * starts and ends, in which nothing is a tag, so that code samples can have annotations like `@main`.
  */
private[scaladoc] trait MarkupSyntax {

  /** The markup of a tag body (or of the description), as a document. */
  def document(text: String): Markup.Document

  /** Where code starts and ends in a comment's `chars`, for the tag layer. One per comment, as it may remember what
    * it looked for.
    */
  def code(chars: Array[Char]): MarkupSyntax.CodeFinder
}

private[scaladoc] object MarkupSyntax {

  trait CodeFinder {

    /** If code starts at `i`, what ends it. */
    def startsAt(i: Int): Option[CodeEnd]
  }

  /** The end of code that started earlier. */
  trait CodeEnd {

    /** Whether the code ends at `i`. */
    def endsAt(i: Int): Boolean
  }

  def apply(syntax: CommentSyntax): MarkupSyntax = syntax match {
    case CommentSyntax.Wiki     => Wiki
    case CommentSyntax.Markdown => Markdown
  }

  /** scaladoc's wiki syntax: code is `{{{ ... }}}` or `<pre>...</pre>`. */
  object Wiki extends MarkupSyntax {

    def document(text: String): Markup.Document = ParseMarkup(text, CommentSyntax.Wiki)

    def code(chars: Array[Char]): CodeFinder = new WikiCode(chars)
  }

  private final class WikiCode(chars: Array[Char]) extends CodeFinder {
    def startsAt(i: Int): Option[CodeEnd] =
      if (at(chars, i, "{{{", ignoreCase = false)) Some(Marker(chars, "}}}", ignoreCase = false))
      else if (at(chars, i, "<pre", ignoreCase = true)) Some(Marker(chars, "</pre>", ignoreCase = true))
      else None
  }

  /** Markdown: code is a fenced block, ```` ``` ```` or `~~~` on lines of their own, or `{{{ ... }}}` or
    * `<pre>...</pre>`, as in wiki.
    */
  object Markdown extends MarkupSyntax {

    def document(text: String): Markup.Document = ParseMarkup(text, CommentSyntax.Markdown)

    def code(chars: Array[Char]): CodeFinder = new MarkdownCode(chars)
  }

  private final class MarkdownCode(chars: Array[Char]) extends CodeFinder {
    private val wiki = new WikiCode(chars)

    // the fences no closer was found for: by character, where the search started and the fence's length. A later
    // fence of the same character, at least as long, has none either, so it isn't looked for again
    private val unclosed = mutable.Map.empty[Char, List[(Int, Int)]]

    def startsAt(i: Int): Option[CodeEnd] =
      fenceAt(chars, i).flatMap { case (fenceChar, length) =>
        // without a closing fence, it's not taken for code here: a forgotten one would hide every tag after it
        val end   = Fence(chars, fenceChar, length)
        val known = unclosed.getOrElse(fenceChar, Nil).exists { case (from, len) => from <= i && len <= length }
        if (known) None
        else {
          var line  = indexOf(chars, '\n', i)
          var found = false
          while (!found && line >= 0) {
            found = (line + 1 to math.min(line + 4, chars.length - 1)).exists(end.endsAt)
            line = indexOf(chars, '\n', line + 1)
          }
          if (found) Some(end)
          else { unclosed(fenceChar) = (i, length) :: unclosed.getOrElse(fenceChar, Nil); None }
        }
      }.orElse(wiki.startsAt(i))
  }

  private def indexOf(chars: Array[Char], c: Char, from: Int): Int = {
    var j = from
    while (j < chars.length && chars(j) != c) j += 1
    if (j < chars.length) j else -1
  }

  // a code fence that opens at `i`: at most 3 spaces into its line, 3 or more backticks or tildes
  private def fenceAt(chars: Array[Char], i: Int): Option[(Char, Int)] =
    if (i >= chars.length || (chars(i) != '`' && chars(i) != '~') || !lineStartWithin3(chars, i)) None
    else {
      val c = chars(i)
      var j = i
      while (j < chars.length && chars(j) == c) j += 1
      if (j - i >= 3) Some((c, j - i)) else None
    }

  private def lineStartWithin3(chars: Array[Char], i: Int): Boolean = {
    var j = i - 1
    while (j >= 0 && j >= i - 4 && chars(j) == ' ') j -= 1
    (j < 0 || chars(j) == '\n') && i - j - 1 <= 3
  }

  /** A closing fence: of the opening fence's character, at least as long, and nothing else on its line. */
  private final case class Fence(chars: Array[Char], fenceChar: Char, length: Int) extends CodeEnd {
    def endsAt(i: Int): Boolean = fenceAt(chars, i).exists { case (c, run) =>
      c == fenceChar && run >= length && {
        var j = i + run
        while (j < chars.length && (chars(j) == ' ' || chars(j) == '\t')) j += 1
        j >= chars.length || chars(j) == '\n'
      }
    }
  }

  private final case class Marker(chars: Array[Char], marker: String, ignoreCase: Boolean) extends CodeEnd {
    def endsAt(i: Int): Boolean = at(chars, i, marker, ignoreCase)
  }

  private def at(chars: Array[Char], i: Int, x: String, ignoreCase: Boolean): Boolean =
    chars.length - i >= x.length && x.indices.forall { j =>
      val c = chars(i + j)
      if (ignoreCase) c.toLower == x.charAt(j).toLower else c == x.charAt(j)
    }
}
