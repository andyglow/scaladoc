package scaladoc.utils

import scala.reflect.api.Position
import scala.reflect.internal.util.NoSourceFile

object SourceCodeUtils {

  def extractComment(pos: Position): Option[String] = {
    pos.source match {
      case NoSourceFile => None
      case src =>
        val str = new String(src.content, 0, src.lineToOffset(pos.line - 1)).stripTrailing()

        if (str.endsWith("*/")) {
          val start = str.lastIndexOf("/**")
          if (start >= 0) {
            var indent = 0
            while ((start - indent - 1) >= 0 && { val c = str.charAt(start - indent - 1)
                     c.isWhitespace && c != '\n' && c != '\r' }) indent += 1
            Some(str.substring(start - indent))
          } else None
        } else None
    }
  }
}
