package scaladoc.utils

import scaladoc.Markup._

trait MarkupUtils {

  private[scaladoc] implicit class SpanOps(private val span: Span) {
    def map(fn: String => String): Span = span match {
      case PlainText(value)   => PlainText(fn(value))
      case Monospace(value)   => Monospace(fn(value))
      case Italic(value)      => Italic(fn(value))
      case Bold(value)        => Bold(fn(value))
      case Underline(value)   => Underline(fn(value))
      case Superscript(value) => Superscript(fn(value))
      case Subscript(value)   => Subscript(fn(value))
      case Link(value)        => Link(fn(value))
    }

    def stripLeading: Span = map(x => x.dropWhile(_.isWhitespace))

    def stripTrailing: Span = map(_.replaceAll("\\s+$", ""))
  }

  private[scaladoc] implicit class SliceOps(private val slice: List[Span]) {

    def trim: List[Span] = {

      def withHeadUpdated(slice: List[Span]): List[Span] =
        slice.headOption match {
          case Some(head) => head.stripLeading +: slice.drop(1)
          case None => Nil
        }

      def withLastUpdated(slice: List[Span]): List[Span] =
        slice.lastOption match {
          case Some(last) => slice.dropRight(1) :+ last.stripTrailing
          case None => Nil
        }

      withLastUpdated(withHeadUpdated(slice))
    }
  }

}
