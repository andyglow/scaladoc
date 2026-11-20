package scaladoc.utils

import scala.reflect.api.Position
import scala.reflect.internal.util.NoSourceFile

object SourceCodeUtils {

  def extractComment(pos: Position): Option[String] = {
    pos.source match {
      case NoSourceFile => None
      case src =>
        def char = src.content

        // traversing from end to start and
        // - when `*/` is found - remember the end position
        // - when `/**` is found - remember start position and exit the loop
        var start: Option[Int] = None
        var end: Option[Int] = None
        var i = src.lineToOffset(pos.line - 1) - 2
        while (
          i >= 0 &&
          start.isEmpty
        ) {
          if (char(i) == '*' && char(i + 1) == '/') end = Some(i + 2)
          if (char(i) == '/' && char(i + 1) == '*' && char(i + 2) == '*') start = Some(i)
          i -= 1
        }

        for {
          start <- start
          end   <- end
        } yield {
          // detect indent by trying to find number of spaces between the
          // start tag and the start of the current line
          // TODO: how do we handle tabs?
          // TODO: what if there are non-whitespace symbol?
          var indent = 0
          while ((start - indent - 1) >= 0 && {
            val c = char(start - indent - 1)
            c.isWhitespace && c != '\n' && c != '\r'
          }) indent += 1

          new String(char, start - indent, end - (start - indent))
        }
    }
  }
}
