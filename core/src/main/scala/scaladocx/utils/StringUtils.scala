package scaladocx.utils

import scala.annotation.tailrec


trait StringUtils {

  private[scaladocx] val EmptyString: String = ""

  private[scaladocx] implicit class CharOps(private val c: Char) {

    def q: String = s"'$c'"

    def isNL: Boolean = {
      c == '\n' || isLineSeparator || isParSep
    }

    def isLineSeparator: Boolean = {
      ((((1 << Character.LINE_SEPARATOR)) >> Character.getType(c)) & 1) != 0
    }

    def isParSep: Boolean = {
      ((((1 << Character.PARAGRAPH_SEPARATOR)) >> Character.getType(c)) & 1) != 0
    }

    def isSpace: Boolean = !isNL && c.isWhitespace
  }


  private[scaladocx] implicit class StringOps(private val chars: String) {

    final def isVain: Boolean = chars.forall(_.isWhitespace)

    final def leadingWS: Int = chars.takeWhile(_.isWhitespace).length

    final def splitByParSep: List[String] = {
      firstParSep match {
        case None             => List(chars)
        case Some((pos, len)) => chars.substring(0, pos) +: chars.substring(pos + len).splitByParSep
      }
    }

    final def parSepTokenized: List[ParSep.Token] = {
      if (chars.isEmpty) Nil else {
        firstParSep match {
          case None             => List(ParSep.Text(chars))
          case Some((pos, len)) => ParSep.Text(chars.substring(0, pos)) +: ParSep.Separator +: chars.substring(pos + len).parSepTokenized
        }
      }
    }

    def firstParSep: Option[(Int, Int)] = {
      var res: Option[(Int, Int)] = None
      findParSep { case (pos, len) =>
        res = Some((pos, len))
        true
      }

      res
    }

    def allParSeps: List[(Int, Int)] = {
      var res: List[(Int, Int)] = Nil
      findParSep { case (pos, len) =>
        res = res :+ (pos, len)
        false
      }

      res
    }

    // finds paragraph separator
    def findParSep(f: (Int, Int) => Boolean): Unit = {
      var pos = 0
      var separatorStartedAt = -1
      var separatorStartedWith = -1 // 0 = ParSep, 1 = NL
      var nlCount = 0
      var brake = false
      def stop() = {
        if (separatorStartedWith == 0 || nlCount > 1) {
          brake = f(separatorStartedAt, pos - separatorStartedAt)
        }
        separatorStartedAt = -1
        separatorStartedWith = -1
        nlCount = 0
      }
      def start(kind: Int) = {
        separatorStartedAt = pos
        separatorStartedWith = kind
        if (kind == 1) nlCount += 1
      }
      while (!brake && pos < chars.length) {
        val c = chars(pos)
        if (separatorStartedAt >=0 ) {
          if (c == '\n') nlCount += 1
          if (!c.isWhitespace) stop()
        } else {
          if (c.isParSep) start(0)
          else if (c == '\n') start(1)
        }

        pos += 1
      }
      if(!brake && nlCount > 1) stop()
    }
  }
}
