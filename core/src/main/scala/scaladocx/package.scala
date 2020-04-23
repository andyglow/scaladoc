package object scaladocx {

  private[scaladocx] val EmptyString: String = ""

  private[scaladocx] implicit class CharOps(private val c: Char) extends AnyVal {

    def q: String = s"'$c'"

    def isNL: Boolean = {
      c == '\n' ||
        ((((1 << Character.LINE_SEPARATOR) |
          (1 << Character.PARAGRAPH_SEPARATOR)) >> Character.getType(c)) & 1) != 0

    }
    def isSpace: Boolean = !isNL && c.isWhitespace
  }
}
