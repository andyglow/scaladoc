package scaladoc.utils

import scala.reflect.api.Position
import scala.reflect.internal.util.NoSourceFile

object SourceCodeUtils {

  /** The doc comment of the declaration at `pos` (the position of a definition: its name, or its first modifier),
    * with the indentation of its first line, or `None` if the declaration has none.
    *
    * A doc comment belongs to the declaration right after it: only whitespace, annotations (with their arguments),
    * modifiers, the definition keyword and ordinary comments may sit in between. That's what scalac's scaladoc and
    * Scala 3's `Symbol.docstring` attach. A doc comment followed by anything else (another definition, a
    * statement, a closing brace) belongs to that, and an undocumented declaration gets `None`. Comment markers inside
    * string and character literals and inside block comments are not comments.
    */
  def extractComment(pos: Position): Option[String] = {
    pos.source match {
      case NoSourceFile => None
      case src =>
        val char = src.content

        new DocCommentScanner(char, math.min(pos.point, char.length)).scan() map { case (start, end) =>
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

  // Words that may sit between a doc comment and the name of the declaration it documents
  private val DeclarationWords: Set[String] = Set(
    // modifiers (`private` and `protected` may be followed by a qualifier, see `Access`)
    "abstract",
    "case",
    "final",
    "implicit",
    "lazy",
    "override",
    "sealed",
    // definition keywords
    "class",
    "def",
    "object",
    "trait",
    "type",
    "val",
    "var"
  )

  /** Lexes `chars` from the start up to `limit` (the declaration), just enough to know where comments, literals and
    * bracketed groups are, and remembers the last doc comment that only declaration words, annotations and ordinary
    * comments follow.
    */
  private final class DocCommentScanner(chars: Array[Char], limit: Int) {
    private val n = chars.length
    private var i = 0

    // tokens
    private final val Eof     = 0
    private final val Word    = 1 // identifier or keyword, `wordStart` until `i`
    private final val Literal = 2 // string, interpolated string or character literal
    private final val Open    = 3 // ( [ {
    private final val Close   = 4 // ) ] }
    private final val At      = 5
    private final val Dot     = 6
    private final val Other   = 7
    private final val Comment = 8 // line or block comment
    private final val Doc     = 9 // doc comment, `commentStart` until `i`

    private var wordStart    = 0
    private var commentStart = 0

    // what follows the last doc comment
    private final val Ready  = 0 // nothing, or modifiers, keywords and complete annotations
    private final val AtSign = 1 // `@`: the annotation's name comes next
    private final val Annot  = 2 // annotation name, or its type or value arguments: more of them may follow
    private final val Path   = 3 // `@a.`: the rest of the name comes next
    private final val Access = 4 // `private` or `protected`: a qualifier may follow
    private final val Broken = 5 // anything else: the doc comment documents something else

    def scan(): Option[(Int, Int)] = {
      var doc: Option[(Int, Int)] = None
      var state                   = Broken

      var token = next()
      while (token != Eof) {
        state = token match {
          case Doc =>
            doc = Some((commentStart, i))
            Ready
          case Comment                                        => state
          case _ if state == Broken                           => Broken
          case At if state != AtSign && state != Path         => AtSign
          case Dot if state == Annot                          => Path
          case Open if state == Annot || state == AtSign      => skipGroup(); Annot
          case Open if state == Access && chars(i - 1) == '[' => skipGroup(); Ready
          case Word if state == AtSign || state == Path       => Annot
          case Word =>
            word match {
              case "private" | "protected"           => Access
              case w if DeclarationWords.contains(w) => Ready
              case _                                 => Broken
            }
          case _ => Broken
        }
        token = next()
      }

      if (state == Broken) None else doc
    }

    private def word: String = new String(chars, wordStart, i - wordStart)

    // after an `Open` token (or the `{` of `${`): skips to the end of its group. Comments and literals inside
    // are skipped as such, and doc comments there (in annotation arguments) belong to nothing.
    private def skipGroup(): Unit = {
      var depth = 1
      while (depth > 0) next(insideGroup = true) match {
        case Open  => depth += 1
        case Close => depth -= 1
        case Eof   => depth = 0
        case _     =>
      }
    }

    private def at(j: Int): Char = if (j < n) chars(j) else '\u0000'

    private def isWordChar(c: Char): Boolean = Character.isLetterOrDigit(c) || c == '_' || c == '$'

    /** The next token. Outside of groups, lexing stops at `limit` (the declaration). */
    private def next(insideGroup: Boolean = false): Int = {
      while (i < n && Character.isWhitespace(chars(i))) i += 1
      if (i >= n || (!insideGroup && i >= limit)) Eof
      else {
        val c = chars(i)
        if (c == '/' && at(i + 1) == '/') {
          while (i < n && chars(i) != '\n' && chars(i) != '\r') i += 1
          Comment
        } else if (c == '/' && at(i + 1) == '*') {
          commentStart = i
          val isDoc = at(i + 2) == '*' && at(i + 3) != '/'
          skipBlockComment()
          if (isDoc) Doc else Comment
        } else if (c == '"') {
          skipString(interpolated = false)
          Literal
        } else if (c == '\'') {
          if (at(i + 1) == '\\') {
            // escaped character literal: '\n', '\'', 'A'
            i += 3
            while (i < n && chars(i) != '\'') i += 1
            i += 1
            Literal
          } else if (at(i + 2) == '\'') {
            i += 3
            Literal
          } else {
            // a symbol literal ('sym), the word follows
            i += 1
            Other
          }
        } else if (c == '`') {
          wordStart = i
          i += 1
          while (i < n && chars(i) != '`') i += 1
          i += 1
          Word
        } else if (isWordChar(c)) {
          wordStart = i
          while (i < n && isWordChar(chars(i))) i += 1
          if (at(i) == '"') {
            // an interpolated string: `s"..."`
            skipString(interpolated = true)
            Literal
          } else Word
        } else {
          i += 1
          c match {
            case '(' | '[' | '{' => Open
            case ')' | ']' | '}' => Close
            case '@'             => At
            case '.'             => Dot
            case _               => Other
          }
        }
      }
    }

    // at `/*`: block comments nest
    private def skipBlockComment(): Unit = {
      var depth = 1
      i += 2
      while (i < n && depth > 0) {
        if (chars(i) == '/' && at(i + 1) == '*') { depth += 1; i += 2 }
        else if (chars(i) == '*' && at(i + 1) == '/') { depth -= 1; i += 2 }
        else i += 1
      }
    }

    // at the opening quote of a string literal
    private def skipString(interpolated: Boolean): Unit = {
      val triple = at(i + 1) == '"' && at(i + 2) == '"'
      i += (if (triple) 3 else 1)
      var done = false
      while (!done && i < n) {
        val c = chars(i)
        if (triple && c == '"' && at(i + 1) == '"' && at(i + 2) == '"') {
          // the closing quotes are the last three of the run: `""""` ends with a quote in the string
          i += 3
          while (at(i) == '"') i += 1
          done = true
        } else if (!triple && c == '"') {
          i += 1
          done = true
        } else if (!triple && (c == '\n' || c == '\r')) {
          done = true // unclosed, doesn't compile
        } else if (!triple && c == '\\') {
          i += 2
        } else if (interpolated && c == '$' && at(i + 1) == '{') {
          // a spliced block: Scala code, with its own strings and comments
          i += 2
          skipGroup()
        } else if (interpolated && c == '$') {
          i += 2 // `$$`, `$"` or the first character of `$name`
        } else {
          i += 1
        }
      }
    }
  }
}
