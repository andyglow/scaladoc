package scaladoc

import scaladoc.parser.{Expectation, MutableTag}
import scaladoc.utils._


sealed trait ScaladocException extends Exception // with NoStackTrace

case object EmptyInput extends ScaladocException

final case class NonScaladocStyle(style: Style.NonScaladoc) extends ScaladocException

sealed trait ParseException extends ScaladocException {
  def line: Int
  def column: Int
  def position: Int
  def message: String
  override def getMessage: String = s"Position: $position ($line:$column). $message"
}

final case class UnexpectedEOF(
  line: Int,
  column: Int,
  position: Int,
  expected: Option[Expectation]) extends ParseException {

  def message = s"Unexpected EOF${expected map { e => s". Expected: $e" } getOrElse ""}"
}

final case class UnexpectedChar(
  line: Int,
  column: Int,
  position: Int,
  got: Char,
  expected: Expectation) extends ParseException {

  def message = s"Unexpected char: ${got.q}. Expected: $expected"
}

final case class BrokenIndentation(
  line: Int,
  column: Int,
  position: Int,
  actual: Int,
  expected: Int) extends ParseException {

  def message = s"Broken indentation. Actual: $actual. Expected: $expected"
}

final case class BufferFlushError(
  line: Int,
  column: Int,
  position: Int,
  tag: Option[MutableTag],
  buffer: StringBuilder) extends ParseException {

  def message = s"Error flushing buffer [$buffer]. ${tag map { t => s"Tag ($t) already closed" } getOrElse "No tags" }"
}

trait TagSpecificException extends ParseException

final case class EmptyGroupPriority(
  line: Int,
  column: Int,
  position: Int) extends TagSpecificException {

  def message = "Group Priority is not specified"
}

trait MarkupException extends ParseException

@deprecated("no longer thrown: markup ends at a paragraph break", "0.3.1")
final case class UnexpectedParagraphSeparator(
  line: Int,
  column: Int,
  position: Int,
  length: Int) extends MarkupException {

  def message = s"Unexpected Paragraph Separator: Len=$length"
}

/** Inline markup that has to be closed isn't, within its paragraph: an HTML tag the model has a span for, like `<b>`,
  * `<code>` or `<a href>`, or a javadoc tag, like `{@code`. `markup` is its opener, `near` how the text goes from it.
  */
final case class UnclosedMarkup(markup: String, near: String) extends ScaladocException {
  override def getMessage: String = s"Unclosed $markup: $near"
}

/** An HTML tag nested deeper than `limit` spans. `near` is how the text goes from it. */
final case class MarkupTooDeep(markup: String, limit: Int, near: String) extends ScaladocException {
  override def getMessage: String = s"$markup nested more than $limit deep: $near"
}
