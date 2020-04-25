package scaladocx.parse

import scaladocx.utils._


private[scaladocx] sealed trait Expectation

private[scaladocx] object Expectation {

  final case class Exact(char: Char) extends Expectation {
    override def toString: String = char.q
  }

  final case class OneOf(chars: ::[Char]) extends Expectation {
    override def toString = s"one of ${chars map { _.q } mkString ", "}"
  }

  final case object EOF extends Expectation

  final case object Whitespace extends Expectation

  final case class Non(char: Char) extends Expectation {
    override def toString: String = s"anything but ${char.q}"
  }
}