package scaladocx

import scaladocx.parse.{Expectation, MutableTag}

import scala.util.control.NoStackTrace

sealed trait ScaladocException extends Exception with NoStackTrace

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

  def message = s"Unexpected EOF${expected map { e => s"Expected: $e" } getOrElse ""}"
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

  def message = s"Group Priority is not specified"
}
