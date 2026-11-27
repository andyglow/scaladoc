package scaladoc.utils




trait StringUtils {

  private[scaladoc] val EmptyString: String = ""

  private[scaladoc] implicit class CharOps(private val c: Char) {

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

    /** Whitespace as HTML has it, in tags and around table cells: a space, a tab, a line feed, a vertical tab, a form
      * feed or a carriage return.
      */
    def isAsciiSpace: Boolean = c == ' ' || c == '\t' || c == '\n' || c == '\u000b' || c == '\f' || c == '\r'

    def isAsciiLetter: Boolean = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

    def isAsciiDigit: Boolean = c >= '0' && c <= '9'

    def isAsciiAlnum: Boolean = isAsciiLetter || isAsciiDigit

    def isHexDigit: Boolean = isAsciiDigit || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

    // a lowercase ASCII letter for an uppercase one
    def toAsciiLower: Char = if (c >= 'A' && c <= 'Z') (c + ('a' - 'A')).toChar else c
  }

  private[scaladoc] implicit class StringOps(private val chars: String) {

    final def isVain: Boolean = chars.forall(_.isWhitespace)

    final def leadingWS: Int = chars.takeWhile(_.isWhitespace).length

    /** Whether `x`, which is lowercase, is at `i`, whatever the case of the ASCII letters here: `<PRE>` is `<pre>`. */
    final def startsWithAsciiLower(x: String, i: Int): Boolean =
      i >= 0 && i + x.length <= chars.length && {
        var k = 0
        while (k < x.length && chars.charAt(i + k).toAsciiLower == x.charAt(k)) k += 1
        k == x.length
      }

    /** Without the `isAsciiSpace` whitespace at the end. If it ends with a line separator (`\u2028`, `\u2029` or
      * `\u0085`) instead, that stays, and the whitespace before it goes.
      */
    final def stripTrailingSpace: String = {
      val separator = chars.nonEmpty && "\u2028\u2029\u0085".indexOf(chars.charAt(chars.length - 1)) >= 0
      val last      = if (separator) chars.length - 1 else chars.length
      var end       = last
      while (end > 0 && chars.charAt(end - 1).isAsciiSpace) end -= 1
      chars.substring(0, end) + chars.substring(last)
    }

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
