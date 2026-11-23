package scaladoc

/** The markup syntax a doc comment is written in, which decides what its text means: `'''x'''`, `__x__` and `=x=`
  * mean different things in different syntaxes, so the parser is told which one a comment uses and never guesses.
  *
  * Only the markup of the comment's text and its tags' bodies depends on it. Tags (`@param`, `@return` and the rest)
  * work the same in every syntax.
  */
sealed trait CommentSyntax extends Product with Serializable

object CommentSyntax {

  /** scaladoc's wiki syntax: `'''bold'''`, `''italic''`, `__underline__`, `=heading=`, `{{{ code }}}`, `[[link]]`.
    * Scala 2's only syntax, and Scala 3's with `-comment-syntax wiki`.
    */
  case object Wiki extends CommentSyntax
}
