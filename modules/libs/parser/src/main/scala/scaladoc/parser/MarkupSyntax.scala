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
    case CommentSyntax.Wiki => Wiki
  }

  /** scaladoc's wiki syntax: code is `{{{ ... }}}` or `<pre>...</pre>`. */
  object Wiki extends MarkupSyntax {

    def document(text: String): Markup.Document = ParseMarkup(text)

    def codeStartsAt(chars: Array[Char], i: Int): Option[CodeEnd] =
      if (at(chars, i, "{{{", ignoreCase = false)) Some(Marker("}}}", ignoreCase = false))
      else if (at(chars, i, "<pre", ignoreCase = true)) Some(Marker("</pre>", ignoreCase = true))
      else None
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
