package scaladoc.parser

import scaladoc._

/** Platform-independent entry point of the parser: plain Scala over `String`, shared by Scala 2 and Scala 3.
  *
  * `Scaladoc.fromString` (see the `scaladoc` package object) delegates here.
  */
object ScaladocParser {

  /** Parses a comment into a `Scaladoc`.
    *
    * @param x the comment text including the opening and closing comment markers, e.g. the raw source text or what Scala 3's
    *   `Symbol.docstring` returns. Leading whitespace before the opening marker is allowed.
    * @param hasTags if `false`, `x` is taken as an already stripped comment body (no comment markers and no left `*` border)
    * @param strict if `true`, the left `*` border of every line must be aligned with the opening marker. Only use
    *   it when `x` keeps the original indentation of the first line (Scala 3's `Symbol.docstring` does not)
    * @param syntax the markup syntax the comment is written in, unless it says otherwise with `@syntax wiki` or
    *   `@syntax markdown`, as Scala 3's scaladoc has it
    */
  def fromString(
    x: String,
    hasTags: Boolean = true,
    strict: Boolean = false,
    syntax: CommentSyntax = CommentSyntax.Wiki): Either[ScaladocException, Scaladoc] =
    within(x, hasTags, strict, syntax, Map.empty)

  /** `fromString`, with the defines of the comment's parents and enclosing classes, which the macros look up. */
  private[scaladoc] def within(
    x: String,
    hasTags: Boolean,
    strict: Boolean,
    syntax: CommentSyntax,
    outer: Map[String, String]): Either[ScaladocException, Scaladoc] = {
    val comment =
      if (hasTags) StripCommentTags.pure(x, strict)
      else Right(StripCommentTags.Comment(Style.Stripped, x))

    comment.flatMap { c =>
      c.style match {
        // `Stripped` only comes from the `hasTags = false` branch above
        case Style.Stripped | Style.Scaladoc =>
          val body = Defines.expand(c.body, outer)
          ParseScaladocTags.pure(body, MarkupSyntax(syntaxTag(body).getOrElse(syntax))).map { doc =>
            Scaladoc(doc.tags.filterNot {
              case Tag.OtherTag("syntax", _) => true
              case _                         => false
            })
          }
        case style: Style.NonScaladoc => Left(NonScaladocStyle(style))
      }
    }
  }

  /** The `@define`s of a comment, for the comments of the classes it's in or extends; none if it isn't scaladoc.
    * Its alignment isn't checked: the comment that uses them is.
    */
  private[scaladoc] def defines(x: String, hasTags: Boolean): Map[String, String] =
    (if (hasTags) StripCommentTags.pure(x, strict = false) else Right(StripCommentTags.Comment(Style.Stripped, x))) match {
      case Right(c) if c.style == Style.Stripped || c.style == Style.Scaladoc => Defines.of(c.body)
      case _                                                                  => Map.empty
    }

  /** The syntax a comment says it's written in, as Scala 3's scaladoc has it: `@syntax wiki` or `@syntax markdown`,
    * on a line of its own. An unknown one is none. The tag isn't in the parsed `Scaladoc`.
    */
  private[scaladoc] def syntaxTag(body: String): Option[CommentSyntax] = {
    var found: Option[CommentSyntax] = None
    var start                        = 0
    while (found.isEmpty && start <= body.length) {
      val end  = { val e = body.indexOf('\n', start); if (e < 0) body.length else e }
      val line = body.substring(start, end).trim
      if (line.startsWith("@syntax") && line.length > 7 && line.charAt(7).isWhitespace)
        found = line.substring(8).trim.toLowerCase match {
          case "wiki"     => Some(CommentSyntax.Wiki)
          case "markdown" => Some(CommentSyntax.Markdown)
          case _          => None
        }
      start = end + 1
    }
    found
  }

  /** Parses a doc comment as Scala 3's `Symbol.docstring` returns it.
    *
    * A docstring starts right at the opening marker: the compiler drops the indentation of the first line, while
    * the following lines keep theirs. Non-strict parsing doesn't care. With `strict = true` the indentation of the
    * first line is restored first (inferred from the second line), so that the alignment of a nested definition's
    * doc is validated just as the Scala 2 `Scaladoc.of` validates source text.
    *
    * @param docstring the raw doc comment, comment markers included
    * @param strict if `true`, the left `*` border of all lines must be aligned
    * @param syntax the markup syntax the comment is written in
    */
  def fromDocstring(
    docstring: String,
    strict: Boolean = false,
    syntax: CommentSyntax = CommentSyntax.Wiki): Either[ScaladocException, Scaladoc] =
    docstringWithin(docstring, strict, syntax, Map.empty)

  /** `fromDocstring`, with the defines of the comment's parents and enclosing classes, which the macros look up. */
  private[scaladoc] def docstringWithin(
    docstring: String,
    strict: Boolean,
    syntax: CommentSyntax,
    outer: Map[String, String]): Either[ScaladocException, Scaladoc] =
    within(if (strict) restoreIndentation(docstring) else docstring, hasTags = true, strict, syntax, outer)

  // `/** a\n    * b\n    */` -> `  /** a\n    * b\n    */`. A scaladoc-style border sits 2 columns to the right of
  // the opening marker, a javadoc-style one 1 column; assuming 2 (but never a negative indentation) satisfies both.
  private[scaladoc] def restoreIndentation(x: String): String = {
    val nl = x.indexOf('\n')
    if (!x.startsWith("/*") || nl < 0) x
    else {
      val border = x.iterator.drop(nl + 1).takeWhile(c => c == ' ' || c == '\t').size
      (" " * math.max(border - 2, 0)) + x
    }
  }
}
