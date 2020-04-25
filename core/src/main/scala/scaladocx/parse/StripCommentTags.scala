package scaladocx.parse

import scaladocx._
import scaladocx.utils._

import scala.collection.mutable

/** Strips comment tags.
  * Validates alignment.
  * Understands notion of
  * - `//` single line comment
  * - `/\*` block comment
  * - `/\**` scaladoc
  *
  * @param strictlyAlligned if true validates the allignment of left bound (aka `//` or `*`)
  * @param chars comment data
  */
final class StripCommentTags private (strictlyAlligned: Boolean, val chars: Array[Char]) extends Tokenizer {
  import StripCommentTags._

  val ml = mutable.ListBuffer.empty[String]

  def run(): Option[Comment] = {

    def readBlockCommentTail(style: Style): Option[Comment] = {
      var ident = col - (if (style == Style.Scaladoc) 2 else 1)
      var break = false
      while (!break) {
        if (isEOF) throwUnexpectedEOF(None)
        ml += takeUntil2 {
          case _ if isNL => 0
          case (c1, c2) if c1 == '*' && c2 == '/' => break = true; 1
        }
        if (!break) {
          val (_, prefixLen) = skipWhile(_.isSpace)
          if (lin == 1 && prefixLen == ident + 1) {
            ident += 1
          }
          while (!isEOF && isNL) next()
          if (isEOF) throwUnexpectedEOF(None)
          if (!isStar) {
            if (strictlyAlligned && prefixLen != ident) throwUnexpected(Expectation.Exact(' '))
            else throwUnexpected(Expectation.Exact('*'))
          }
          if (strictlyAlligned && prefixLen != ident) throwBrokenIndentation(prefixLen, ident)
          if (!isEOF) {
            next()
            if (isSlash) break = true
          }
        }
      }
      Some(Comment(style, ml))
    }

    skipUntilChar('/')
    if(isEOF) None else {
      val ident = col
      next()

      // check line comment
      if (isSlash) {
        next()
        ml += takeUntilNL()
        var gap = false
        while (!isEOF && !gap) {
          val start = pos
          val (_, prefixLen) = skipUntil {
            case _ if isNL && pos != start => gap = true; 0
            case (c1, c2) if c1 == '/' && c2 == '/' => 1
          }
          if (strictlyAlligned && prefixLen != ident) throwBrokenIndentation(prefixLen, ident)
          if (!isEOF && !gap) {
            ml += takeUntilNL()
            next()
          }
        }

        Some(Comment(Style.Line, ml))
      } else if (isStar) {
        next()
        if (isStar) {
          // check scaladoc
          next()
          if (isStar) {
            // handling '/***/'
            if (!isEOF && chars(pos + 1) == '/') Some(Comment(Style.Scaladoc, EmptyString))
            else throwUnexpectedChar(Expectation.Non('*'))
          } else if (isSlash) {
            // handling '/**/'
            Some(Comment(Style.Block, EmptyString))
          } else {
            readBlockCommentTail(Style.Scaladoc)
          }
        } else {
          // check block comment
          readBlockCommentTail(Style.Block)
        }
      } else
        throwUnexpected(Expectation.OneOf(::('/', List('*'))))
    }
  }
}

private[scaladocx] object StripCommentTags {

  final case class Comment(style: Style, body: String)
  final object Comment {

    def apply(style: Style, lines: Iterable[String]): Comment = {
      Comment(style, lines.trim.mkString("\n"))
    }
  }

  def apply(x: String, strict: Boolean = false): Option[Comment] = {
    new StripCommentTags(strict, x.toCharArray).run()
  }

  def pure(x: String, strict: Boolean = false): Either[ScaladocException, Comment] = {
    try {
      StripCommentTags(x, strict) match {
        case None        => Left(EmptyInput)
        case Some(value) => Right(value)
      }
    } catch {
      case ex: ScaladocException => Left(ex)
    }
  }
}