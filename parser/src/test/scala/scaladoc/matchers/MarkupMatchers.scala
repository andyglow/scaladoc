package scaladoc.matchers

import org.scalatest.matchers.{MatchResult, Matcher}
import scaladoc._
import scaladoc.Markup._

trait MarkupMatchers {

  class MarkupMatcher(right: Markup) extends Matcher[Markup] {

    override def apply(left: Markup): MatchResult = {
      def ok(): MatchResult = MatchResult(matches = true, "", "")

      def fail(path: List[String], failMsg: String = "", negatedFailMsg: String = ""): MatchResult = MatchResult(matches = false, failMsg, negatedFailMsg)

      def result(path: List[String], actual: String, expect: String): MatchResult = {
        val pathS = path mkString "/"
        MatchResult(matches = expect == actual, s"Expected: '$expect' but got '$actual' at $pathS", pathS)
      }

      def traverse(path: List[String], l: Markup, r: Markup): MatchResult = {
        (l, r) match {
          case (PlainText(l), PlainText(r))       => result(path :+ "plain-text", l, r)
          case (Monospace(l), Monospace(r))       => result(path :+ "monospace", l, r)
          case (Italic(l), Italic(r))             => result(path :+ "italic", l, r)
          case (Bold(l), Bold(r))                 => result(path :+ "bold", l, r)
          case (Underline(l), Underline(r))       => result(path :+ "underline", l, r)
          case (Superscript(l), Superscript(r))   => result(path :+ "superscript", l, r)
          case (Subscript(l), Subscript(r))       => result(path :+ "subscript", l, r)
          case (Link(l), Link(r))                 => result(path :+ "link", l, r)
          case (CodeBlock(l), CodeBlock(r))       => result(path :+ "code-block", l, r)
          case (Heading(ll, l), Heading(rr, r))   =>
            if (ll != rr) fail(
              path :+ s"heading",
              s"Expected heading level is $rr, but got $ll") else result(path :+ s"heading-${ll.productPrefix.toLowerCase}", l, r)
          case (Paragraph(l), Paragraph(r))       =>
            if (l.size != r.size) fail(path :+ "paragraph", s"children size are not equal (expect ${r.size}, got ${l.size})") else {
              l.zip(r).foldLeft(ok()) {
                case (acc, (l, r)) if acc.matches => traverse(path :+ "paragraph", l, r)
                case (acc, _)                     => acc
              }
            }
          case (Document(l), Document(r))       =>
            if (l.size != r.size) fail(path :+ "document", s"children size are not equal (expect ${r.size}, got ${l.size})") else {
              l.zip(r).foldLeft(ok()) {
                case (acc, (l, r)) if acc.matches => traverse(path :+ "document", l, r)
                case (acc, _)                     => acc
              }
            }
          case (l, r)                           => fail(path, s"$l doesn't match $r")
        }
      }

      traverse(Nil, left, right)
    }
  }

  def coincideWith(right: Markup): Matcher[Markup] = new MarkupMatcher(right)
}

object MarkupMatchers extends MarkupMatchers
