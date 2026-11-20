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
    */
  def fromString(x: String, hasTags: Boolean = true, strict: Boolean = false): Either[ScaladocException, Scaladoc] = {
    val comment =
      if (hasTags) StripCommentTags.pure(x, strict)
      else Right(StripCommentTags.Comment(Style.Stripped, x))

    comment.flatMap { c =>
      c.style match {
        // `Stripped` only comes from the `hasTags = false` branch above
        case Style.Stripped | Style.Scaladoc => ParseScaladocTags.pure(c.body)
        case style: Style.NonScaladoc        => Left(NonScaladocStyle(style))
      }
    }
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
    */
  def fromDocstring(docstring: String, strict: Boolean = false): Either[ScaladocException, Scaladoc] =
    fromString(if (strict) restoreIndentation(docstring) else docstring, strict = strict)

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
