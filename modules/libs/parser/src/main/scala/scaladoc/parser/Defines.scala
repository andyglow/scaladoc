package scaladoc.parser

import scala.collection.mutable.ListBuffer

/** Scaladoc's macros: `@define name text` defines a variable, and `$name` or `${name}` in the comment is its text.
  * `\$` is a `$`. Unknown variables stay as they are.
  *
  * A comment also uses the defines of its class's parents and enclosing classes, which only the compiler knows: the
  * macros look them up, in scaladoc's order, and pass them in as `outer`. A comment's own defines come first.
  */
private[scaladoc] object Defines {

  // how deep a variable can expand into others, which keeps a define that uses itself from looping forever
  private val MaxDepth = 10

  /** The comment body without its `@define` sections, which run up to the next tag, and its variables expanded, with
    * its own defines and then `outer`'s.
    */
  def expand(body: String, outer: Map[String, String] = Map.empty): String = {
    val (kept, own) = split(body)
    val defines     = outer ++ own
    if (defines.isEmpty) body else substitute(kept, defines, 0)
  }

  /** The `@define`s of a comment body. Of two with the same name, the later one counts, as in scaladoc. */
  def of(body: String): Map[String, String] = split(body)._2.toMap

  /** Defines of several comments, in the order they're looked up in: the first one with a name wins. */
  def inOrder(defines: Seq[Map[String, String]]): Map[String, String] =
    defines.foldRight(Map.empty[String, String])((first, rest) => rest ++ first)

  // the body's lines without the `@define` sections, and the defines, in order
  private def split(body: String): (String, List[(String, String)]) = {
    val defines = ListBuffer.empty[(String, String)]
    val kept    = ListBuffer.empty[String]
    var current = Option.empty[(String, ListBuffer[String])]
    def close(): Unit = {
      current foreach { case (name, lines) => defines += name -> lines.mkString("\n").trim }
      current = None
    }
    lines(body) foreach { line =>
      defineAt(line) match {
        case Some((name, text))                        => close(); current = Some(name -> ListBuffer(text))
        case None if current.isDefined && !isTag(line) => current.get._2 += line
        case None                                      => close(); kept += line
      }
    }
    close()
    (kept.mkString("\n"), defines.toList)
  }

  private def lines(body: String): List[String] = {
    val out   = ListBuffer.empty[String]
    var start = 0
    var nl    = body.indexOf('\n')
    while (nl >= 0) {
      out += body.substring(start, nl)
      start = nl + 1
      nl = body.indexOf('\n', start)
    }
    out += body.substring(start)
    out.toList
  }

  private def isNameChar(c: Char) = c.isLetterOrDigit || c == '_'

  private def skipSpaces(line: String, from: Int): Int = {
    var i = from
    while (i < line.length && line.charAt(i).isWhitespace) i += 1
    i
  }

  // `@define name text`, after any indentation: the name and the text
  private def defineAt(line: String): Option[(String, String)] = {
    val at  = skipSpaces(line, 0)
    val end = at + "@define".length
    if (!line.startsWith("@define", at) || end >= line.length || !line.charAt(end).isWhitespace) None
    else {
      val from = skipSpaces(line, end)
      var to   = from
      while (to < line.length && isNameChar(line.charAt(to))) to += 1
      if (to == from) None else Some(line.substring(from, to) -> line.substring(skipSpaces(line, to)))
    }
  }

  // a line that starts a tag, `@name`, which ends a define
  private def isTag(line: String): Boolean = {
    val at = skipSpaces(line, 0)
    at + 1 < line.length && line.charAt(at) == '@' && isNameChar(line.charAt(at + 1))
  }

  private def substitute(text: String, defines: Map[String, String], depth: Int): String = {
    val out = new StringBuilder
    var i   = 0
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
