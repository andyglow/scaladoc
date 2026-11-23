package scaladoc.ofmodels

// The documented models are in wiki syntax: the ones whose markup is wiki's say so with `@syntax wiki`, which Scala 3
// reads as Markdown otherwise. They live in this file on purpose: Scala 2 reads the doc comment from the source text,
// which is only available when the model's file is compiled in the same run as the `Scaladoc.of` call site
// (Scala 3 without `-Yread-docs` has the same limitation). `Undocumented` has its own file, see there.

/** = Kitchen sink =
  *
  * Description with ''italic'', '''bold''', `monospace`, __underline__, ^sup^, ,,sub,, and [[scala.Option]].
  *
  * {{{
  * val x = 1
  * }}}
  *
  * @tparam T the type param
  * @param a the a param
  *   continued on the next line
  * @param b the b param
  * @constructor the constructor
  * @return nothing really
  * @throws java.lang.IllegalStateException when broken
  * @see [[scala.Option]]
  * @note a note
  * @example an example
  * @usecase a use case
  * @author someone
  * @version 1.0
  * @since 0.1
  * @todo a todo
  * @deprecated deprecated
  * @migration a migration
  * @group g1
  * @groupname g1 Group One
  * @groupdesc g1 the group description
  * @groupprio g1 10
  * @documentable
  * @inheritdoc
  * @custom a custom tag
  * @syntax wiki
  */
case class KitchenSink[T](a: String, b: T)

/** = Level 1 =
  * == Level 2 ==
  * === Level 3 ===
  * ==== Level 4 ====
  * ===== Level 5 =====
  * ====== Level `6` ======
  *
  * '''bold ''italic'' [[scala.Option]]''', __''a''__, x^''2''^ and y,,'''i''',,, see [[https://scala-lang.org the ''Scala'' site]].
  * @syntax wiki
  */
class NestedMarkup

/** Lists:
  *
  *   - an '''item'''
  *     a. nested
  *     a. and [[scala.Option]]
  *   - last
  *
  * @param a one of
  *   I. x
  *   I. y
  * @syntax wiki
  */
class Lists(a: String)

/** Sizes:
  *
  * | Size | Price |
  * |:-----|------:|
  * | S    | `1`   |
  * | M    |
  *
  * ----
  *
  * The end.
  */
class Tables

/** <b>Bold</b>, <code>a &lt; b</code>, <a href="https://x.y">a link</a> and <span>a span</span>.
  *
  * @syntax wiki
  */
class Html

/** Markdown, as it says: ~~gone~~, ![the logo](logo.png), a break\
  * here, and a note[^1].
  *
  * > quoted
  *
  * - [x] done
  *
  * ```scala
  * val x = 1
  * ```
  *
  * [^1]: The note.
  *
  * @syntax markdown
  */
class MarkdownSink

/** A plain class.
  *
  * Second paragraph.
  *
  * @param x the x param
  * @param y the y param
  */
class Plain(x: Int, y: Int)

/**
 * Javadoc style.
 *
 * @param a the a
 */
case class JavadocStyle(a: Int)

/** A trait. */
trait SomeTrait

object Outer {

  /** Nested, so indented.
    *
    * @param a the a
    *   continued
    * @param b the b
    * @syntax wiki
    */
  case class Nested(a: String, b: Int)

  object Deeper {

    /**
     * Javadoc style, nested.
     *
     * @param a the a
     */
    case class Nested2(a: String)
  }
}

// `Scaladoc.of[T]` reads the source text on Scala 2 and `Symbol.docstring` on Scala 3. Both must give what
// parsing the docstring gives.
class OfSpec extends scaladoc.FSpec {
  import scaladoc._

  private def parsed(docstring: String): Scaladoc =
    Scaladoc.fromDocstring(docstring, strict = true).fold(err => fail(err.getMessage), identity)

  test("undocumented") {
    Scaladoc.of[Undocumented] mustBe None
  }

  test("all tags and markup") {
    val doc = Scaladoc.of[KitchenSink[Int]]
    doc mustBe Some(parsed(Docstrings.KitchenSink))
    doc.get.tags.size mustBe 24
    doc.get.textParams mustBe Map("a" -> "the a param\ncontinued on the next line", "b" -> "the b param")
  }

  test("nested markup and all heading levels") {
    val doc = Scaladoc.of[NestedMarkup]
    doc mustBe Some(parsed(Docstrings.NestedMarkup))
    doc.get.descriptions.head match {
      case Markup.Document(elements) =>
        elements.collect { case h: Markup.Heading => h.level.value } mustBe List(1, 2, 3, 4, 5, 6)
        elements.last mustBe Markup.Paragraph(
          Markup.Bold(Markup.PlainText("bold "), Markup.Italic("italic"), Markup.PlainText(" "), Markup.Link("scala.Option")),
          Markup.PlainText(", "),
          Markup.Underline(Markup.Italic("a")),
          Markup.PlainText(", x"),
          Markup.Superscript(Markup.Italic("2")),
          Markup.PlainText(" and y"),
          Markup.Subscript(Markup.Bold("i")),
          Markup.PlainText(", see "),
          Markup.Link("https://scala-lang.org", List(Markup.PlainText("the "), Markup.Italic("Scala"), Markup.PlainText(" site"))),
          Markup.PlainText("."))
      case other => fail(s"not a document: $other")
    }
  }

  test("lists") {
    val doc = Scaladoc.of[Lists]
    doc mustBe Some(parsed(Docstrings.Lists))
    import Markup._
    doc.get.descriptions mustBe List(Document(
      Paragraph(PlainText("Lists:")),
      UnorderedList(List(
        Document(
          Paragraph(PlainText("an "), Bold("item")),
          OrderedList(List(Document(Paragraph(PlainText("nested"))), Document(Paragraph(PlainText("and "), Link("scala.Option")))), OrderedList.LowerAlpha)),
        Document(Paragraph(PlainText("last")))))))
    doc.get.params("a") mustBe Document(
      Paragraph(PlainText("one of")),
      OrderedList(List(Document(Paragraph(PlainText("x"))), Document(Paragraph(PlainText("y")))), OrderedList.UpperRoman))
  }

  test("tables") {
    val doc = Scaladoc.of[Tables]
    doc mustBe Some(parsed(Docstrings.Tables))
    import Markup._
    def cell(spans: Span*) = Paragraph(spans.toList)
    doc.get.descriptions mustBe List(Document(
      Paragraph(PlainText("Sizes:")),
      Table(
        Table.Row(List(cell(PlainText("Size")), cell(PlainText("Price")))),
        List(Table.Alignment.Left, Table.Alignment.Right),
        List(Table.Row(List(cell(PlainText("S")), cell(Monospace("1")))), Table.Row(List(cell(PlainText("M")), cell())))),
      HorizontalRule,
      Paragraph(PlainText("The end."))))
  }

  test("Markdown, when the comment says so, with Markdown's own nodes") {
    import Markup._
    val doc = Scaladoc.of[MarkdownSink]
    doc mustBe Some(parsed(Docstrings.MarkdownSink))
    doc.get.descriptions mustBe List(Document(
      Paragraph(
        PlainText("Markdown, as it says: "), Strikethrough("gone"), PlainText(", "), Image("logo.png", "the logo"),
        PlainText(", a break"), LineBreak, PlainText("here, and a note"), FootnoteReference("1"), PlainText(".")),
      BlockQuote(Paragraph(PlainText("quoted"))),
      UnorderedList(List(Document(Paragraph(Checkbox(checked = true), PlainText(" done"))))),
      CodeBlock("val x = 1", "scala"),
      Footnote("1", Document(Paragraph(PlainText("The note."))))
    ))
  }

  test("HTML") {
    val doc = Scaladoc.of[Html]
    doc mustBe Some(parsed(Docstrings.Html))
    import Markup._
    doc.get.descriptions mustBe List(Document(Paragraph(List(
      Bold("Bold"), PlainText(", "), Monospace("a < b"), PlainText(", "), Link("https://x.y", "a link"),
      PlainText(" and "), HtmlTag("<span>"), PlainText("a span"), HtmlTag("</span>"), PlainText(".")))))
  }

  test("plain class") {
    val doc = Scaladoc.of[Plain]
    doc mustBe Some(parsed(Docstrings.Plain))
    doc.get.textDescriptions mustBe Seq("A plain class.\nSecond paragraph.")
    doc.get.textParams mustBe Map("x" -> "the x param", "y" -> "the y param")
  }

  test("javadoc style") {
    Scaladoc.of[JavadocStyle] mustBe Some(parsed(Docstrings.JavadocStyle))
  }

  test("trait") {
    Scaladoc.of[SomeTrait] mustBe Some(parsed(Docstrings.SomeTrait))
    Scaladoc.of[SomeTrait].get.textDescriptions mustBe Seq("A trait.")
  }

  test("nested (indented) classes") {
    Scaladoc.of[Outer.Nested] mustBe Some(parsed(Docstrings.Nested))
    Scaladoc.of[Outer.Nested].get.textParams mustBe Map("a" -> "the a\ncontinued", "b" -> "the b")
    Scaladoc.of[Outer.Deeper.Nested2] mustBe Some(parsed(Docstrings.Nested2))
  }
}
