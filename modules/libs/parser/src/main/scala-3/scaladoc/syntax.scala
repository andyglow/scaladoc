package scaladoc

/** The syntax `Scaladoc.of` reads doc comments in, on Scala 3. It's Markdown, Scala 3's default, unless a given in
  * scope says otherwise, for a codebase that writes its comments in wiki syntax (as with `-comment-syntax wiki`):
  *
  * {{{
  * import scaladoc.syntax.wiki.given
  * }}}
  *
  * A comment can also say it for itself, as in Scala 3's scaladoc: `@syntax wiki`.
  */
object syntax {

  /** scaladoc's wiki syntax. */
  object wiki {
    given CommentSyntax.Wiki.type = CommentSyntax.Wiki
  }

  /** Markdown, as without a given. */
  object markdown {
    given CommentSyntax.Markdown.type = CommentSyntax.Markdown
  }
}
