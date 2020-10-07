package scaladocx.macros

import scala.reflect.api.Position
import scala.reflect.internal.util.NoSourceFile
import scala.reflect.macros.blackbox


trait ExtractScaladoc {

  def getScaladoc(pos: Position): Option[String] = {
    pos.source match {
      case NoSourceFile => None
      case src =>
        val str = new String(src.content, 0, src.lineToOffset(pos.line - 1)).trim
        if (str.endsWith("*/")) {
          val start = str.lastIndexOf("/**")
          if (start >= 0) {
            Some(str.substring(start))
          } else None
        } else None
    }
  }
}
