package scaladoc.parser

import scala.collection.mutable.ListBuffer

/** Scaladoc's macros: `@define name text` defines a variable, and `$name` or `${name}` in the comment is its text.
  * `\$` is a `$`. Unknown variables stay as they are.
  *
  * Scaladoc also takes the defines of the comments of enclosing and parent classes, which only the compiler knows;
  * here a comment has its own.
  */
private[scaladoc] object Defines {

  private val DefineLine = """\s*@define\s+(\w+)\s*(.*)""".r
  private val TagLine    = """\s*@\w+.*""".r

  // how deep a variable can expand into others, which keeps a define that uses itself from looping forever
  private val MaxDepth = 10

  /** The comment body without its `@define` sections, which run up to the next tag, and its variables expanded. */
  def expand(body: String): String = {
    val defines = ListBuffer.empty[(String, String)]
    val kept    = ListBuffer.empty[String]
    var current = Option.empty[(String, ListBuffer[String])]
    def close(): Unit = {
      current foreach { case (name, lines) => defines += name -> lines.mkString("\n").trim }
      current = None
    }
    body.split("\n", -1) foreach {
      case DefineLine(name, text)                         => close(); current = Some(name -> ListBuffer(text))
      case line if current.isDefined && !TagLine.pattern.matcher(line).matches() => current.get._2 += line
      case line                                           => close(); kept += line
    }
    close()
    if (defines.isEmpty) body else substitute(kept.mkString("\n"), defines.toMap, 0)
  }

  private def substitute(text: String, defines: Map[String, String], depth: Int): String = {
    val out = new StringBuilder
    var i   = 0
    def isNameChar(c: Char) = c.isLetterOrDigit || c == '_'
    while (i < text.length) {
      val c = text.charAt(i)
      if (c == '\\' && i + 1 < text.length && text.charAt(i + 1) == '$') {
        out += '$'
        i += 2
      } else if (c == '$') {
        val braced = i + 1 < text.length && text.charAt(i + 1) == '{'
        val from   = if (braced) i + 2 else i + 1
        var j      = from
        while (j < text.length && isNameChar(text.charAt(j))) j += 1
        val name   = text.substring(from, j)
        val end    = if (braced) { if (j < text.length && text.charAt(j) == '}') j + 1 else -1 } else j
        defines.get(name) match {
          case Some(value) if name.nonEmpty && end > 0 =>
            out ++= (if (depth < MaxDepth) substitute(value, defines, depth + 1) else value)
            i = end
          case _ =>
            out += c
            i += 1
        }
      } else {
        out += c
        i += 1
      }
    }
    out.toString
  }
}
