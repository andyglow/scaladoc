package scaladoc.compile

import java.nio.file.{FileSystems, Paths}
import java.util.regex.Pattern

import scala.util.Try

/** Decides which classes get their scaladoc embedded into bytecode.
  *
  * Configured with plugin options (each can be given several times):
  *   - `include:path:<glob>` / `exclude:path:<glob>`: a glob over the source file path, in `java.nio` glob syntax.
  *     The path is absolute, so patterns usually start with a double-star segment, e.g. a double star followed by
  *     `/model/` and another double star.
  *   - `include:class:<pattern>` / `exclude:class:<pattern>`: a pattern over the fully-qualified class name, with
  *     nested classes written with dots (`com.acme.Outer.Inner`). `*` matches within one name segment,
  *     `**` across segments (`com.acme.**`), and `**.` matches zero or more packages (`com.acme.**.internal.*`).
  *
  * A class is embedded if it is included and not excluded. With no include options everything is included;
  * otherwise a class is included when its file matches an include path or its name matches an include class.
  */
final class EmbedFilter private (
  includePaths: List[EmbedFilter.Matcher],
  excludePaths: List[EmbedFilter.Matcher],
  includeClasses: List[EmbedFilter.Matcher],
  excludeClasses: List[EmbedFilter.Matcher]) {

  private def includesAll: Boolean = includePaths.isEmpty && includeClasses.isEmpty

  /** `false` if no class of the file can be embedded, so the whole compile unit can be skipped. */
  def acceptsFile(path: String): Boolean =
    !excludePaths.exists(_(path)) && (includesAll || includeClasses.nonEmpty || includePaths.exists(_(path)))

  /** Whether the scaladoc of class `className` declared in file `path` gets embedded. */
  def accepts(path: String, className: String): Boolean = {
    val included = includesAll || includePaths.exists(_(path)) || includeClasses.exists(_(className))
    included && !excludePaths.exists(_(path)) && !excludeClasses.exists(_(className))
  }
}

object EmbedFilter {

  type Matcher = String => Boolean

  /** Embeds every class. */
  val All: EmbedFilter = new EmbedFilter(Nil, Nil, Nil, Nil)

  val usage: String =
    "include:path:<glob>, exclude:path:<glob>, include:class:<pattern> or exclude:class:<pattern>"

  private final case class Rule(mode: String, target: String, matcher: Matcher)

  /** Parses plugin options; `Left` holds an error message for the first invalid one. */
  def parse(options: List[String]): Either[String, EmbedFilter] = {
    val parsed = options map parseOption
    parsed collectFirst { case Left(err) => err } match {
      case Some(err) => Left(err)
      case None =>
        val rules = parsed collect { case Right(rule) => rule }
        def of(mode: String, target: String) = rules collect { case Rule(`mode`, `target`, m) => m }
        Right(new EmbedFilter(of("include", "path"), of("exclude", "path"), of("include", "class"), of("exclude", "class")))
    }
  }

  private def parseOption(option: String): Either[String, Rule] = option.split(":", 3) match {
    case Array(mode @ ("include" | "exclude"), "path", glob) if glob.nonEmpty =>
      pathMatcher(glob).map(Rule(mode, "path", _))
    case Array(mode @ ("include" | "exclude"), "class", pattern) if pattern.nonEmpty =>
      Right(Rule(mode, "class", classMatcher(pattern)))
    case _ =>
      Left(s"invalid option '$option', expected $usage")
  }

  private def pathMatcher(glob: String): Either[String, Matcher] =
    Try(FileSystems.getDefault.getPathMatcher("glob:" + glob)).toEither match {
      case Left(err) => Left(s"invalid path glob '$glob': ${err.getMessage}")
      case Right(m)  => Right(path => Try(m.matches(Paths.get(path))).getOrElse(false))
    }

  private def classMatcher(pattern: String): Matcher = {
    val regex = classPatternRegex(pattern)
    name => regex.matcher(name).matches()
  }

  private[compile] def classPatternRegex(pattern: String): Pattern = {
    val sb = new StringBuilder
    var i  = 0
    def at(j: Int): Char = if (j < pattern.length) pattern.charAt(j) else '\u0000'
    while (i < pattern.length) {
      pattern.charAt(i) match {
        case '*' if at(i + 1) == '*' && at(i + 2) == '.' => sb ++= "(?:[^.]+\\.)*"; i += 3 // zero or more packages
        case '*' if at(i + 1) == '*'                     => sb ++= ".*"; i += 2
        case '*'                                         => sb ++= "[^.]*"; i += 1
        case '?'                                         => sb ++= "[^.]"; i += 1
        case c                                           => sb ++= Pattern.quote(c.toString); i += 1
      }
    }
    Pattern.compile(sb.toString)
  }
}
