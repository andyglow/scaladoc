package scaladoc.utils

import scaladoc.Markup._

trait MarkupUtils {

  private[scaladoc] implicit class SliceOps(private val slice: List[Span]) {

    /** Without the whitespace before the first text and after the last one. A text left empty goes. */
    def trim: List[Span] = {
      def stripHead(spans: List[Span], strip: String => String): List[Span] = spans match {
        case PlainText(text) :: rest =>
          val stripped = strip(text)
          if (stripped.isEmpty) stripHead(rest, strip) else PlainText(stripped) :: rest
        case other => other
      }
      stripHead(stripHead(slice, _.dropWhile(_.isWhitespace)).reverse, _.stripTrailingSpace).reverse
    }
  }
}
