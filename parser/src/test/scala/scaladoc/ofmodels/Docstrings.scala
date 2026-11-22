package scaladoc.ofmodels

object Docstrings {

  // what Scala 3's `Symbol.docstring` returns for the classes above: the comment starts at the opening marker,
  // the first line's indentation is gone, the following lines keep theirs.

  val NestedMarkup: String = Seq(
    "/** = Level 1 =",
    "  * == Level 2 ==",
    "  * === Level 3 ===",
    "  * ==== Level 4 ====",
    "  * ===== Level 5 =====",
    "  * ====== Level `6` ======",
    "  *",
    "  * '''bold ''italic'' [[scala.Option]]''', __''a''__, x^''2''^ and y,,'''i''',,, see [[https://scala-lang.org the ''Scala'' site]].",
    "  */"
  ).mkString("\n")

  val Lists: String = Seq(
    "/** Lists:",
    "  *",
    "  *   - an \'\'\'item\'\'\'",
    "  *     a. nested",
    "  *     a. and [[scala.Option]]",
    "  *   - last",
    "  *",
    "  * @param a one of",
    "  *   I. x",
    "  *   I. y",
    "  */"
  ).mkString("\n")

  val Tables: String = Seq(
    "/** Sizes:",
    "  *",
    "  * | Size | Price |",
    "  * |:-----|------:|",
    "  * | S    | `1`   |",
    "  * | M    |",
    "  *",
    "  * ----",
    "  *",
    "  * The end.",
    "  */"
  ).mkString("\n")

  val KitchenSink: String = Seq(
    "/** = Kitchen sink =",
    "  *",
    "  * Description with ''italic'', '''bold''', `monospace`, __underline__, ^sup^, ,,sub,, and [[scala.Option]].",
    "  *",
    "  * {{{",
    "  * val x = 1",
    "  * }}}",
    "  *",
    "  * @tparam T the type param",
    "  * @param a the a param",
    "  *   continued on the next line",
    "  * @param b the b param",
    "  * @constructor the constructor",
    "  * @return nothing really",
    "  * @throws java.lang.IllegalStateException when broken",
    "  * @see [[scala.Option]]",
    "  * @note a note",
    "  * @example an example",
    "  * @usecase a use case",
    "  * @author someone",
    "  * @version 1.0",
    "  * @since 0.1",
    "  * @todo a todo",
    "  * @deprecated deprecated",
    "  * @migration a migration",
    "  * @group g1",
    "  * @groupname g1 Group One",
    "  * @groupdesc g1 the group description",
    "  * @groupprio g1 10",
    "  * @documentable",
    "  * @inheritdoc",
    "  * @custom a custom tag",
    "  */"
  ).mkString("\n")

  val Plain: String = Seq(
    "/** A plain class.",
    "  *",
    "  * Second paragraph.",
    "  *",
    "  * @param x the x param",
    "  * @param y the y param",
    "  */"
  ).mkString("\n")

  val JavadocStyle: String = Seq(
    "/**",
    " * Javadoc style.",
    " *",
    " * @param a the a",
    " */"
  ).mkString("\n")

  val SomeTrait: String = "/** A trait. */"

  val Nested: String = Seq(
    "/** Nested, so indented.",
    "    *",
    "    * @param a the a",
    "    *   continued",
    "    * @param b the b",
    "    */"
  ).mkString("\n")

  val Nested2: String = Seq(
    "/**",
    "     * Javadoc style, nested.",
    "     *",
    "     * @param a the a",
    "     */"
  ).mkString("\n")
}
