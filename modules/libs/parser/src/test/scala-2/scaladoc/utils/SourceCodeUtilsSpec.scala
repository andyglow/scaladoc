package scaladoc.utils

import scaladoc._

import scala.reflect.internal.util.{BatchSourceFile, OffsetPosition, Position, RangePosition, SourceFile}
import scala.reflect.io.VirtualFile


class SourceCodeUtilsSpec extends FSpec {
  import SourceCodeUtilsSpec._

  test("no indentation") {
    val pos = makePosNextLine(
      s"""/** foo
         |  *
         |  */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """/** foo
        |  *
        |  */""".stripMargin)
  }

  test("indent: 2") {
    val pos = makePosNextLine(
      s"""  /** foo
         |    *
         |    */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """  /** foo
        |    *
        |    */""".stripMargin)
  }

  test("indent: 4") {
    val pos = makePosNextLine(
      s"""    /** foo
         |      *
         |      */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """    /** foo
        |      *
        |      */""".stripMargin)
  }

  test("indent: 4 + prefix") {
    val pos = makePosNextLine(
      s"""    // some more comments
         |    /** foo
         |      *
         |      */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """    /** foo
        |      *
        |      */""".stripMargin)
  }

  test("followed by annotation. same line") {
    val pos = makePosSameLine(
      s"""/** foo
         |  *
         |  */ @annotation
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """/** foo
        |  *
        |  */""".stripMargin)
  }

  test("followed by annotation. next line") {
    val pos = makePosSameLine(
      s"""/** foo
         |  *
         |  */
         |@annotation
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """/** foo
        |  *
        |  */""".stripMargin)
  }

  test("followed by several annotations") {
    val pos = makePosSameLine(
      s"""/** foo
         |  *
         |  */
         |@anno1 @anno2(foo = "bar")
         |@complextAnnotation(a = 22, b = classOf[Foo])
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """/** foo
        |  *
        |  */""".stripMargin)
  }

  test("capture only the last block") {
    val pos = makePosSameLine(
      s"""/** foo
         |  * foo
         |  */
         |/** bar
         |  * bar
         |  */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) mustBe Some(
      """/** bar
        |  * bar
        |  */""".stripMargin)
  }

  // Real declarations, with the position the macro and the compiler plugin get: the offset of the name. A doc
  // comment belongs only to the declaration right after it: whitespace, annotations, modifiers and ordinary
  // comments may sit in between (as for scalac's scaladoc and Scala 3's `Symbol.docstring`), nothing else.

  test("undocumented class after a documented one") {
    val src =
      """/** A */
        |case class A(a: Int)
        |
        |case class B(b: Int)
        |""".stripMargin
    extract(src, "A") mustBe Some("/** A */")
    extract(src, "B") mustBe None
  }

  test("undocumented class after a documented method") {
    val src =
      """object O {
        |
        |  /** m
        |    * @param x the x
        |    */
        |  def m(x: Int): Int = x
        |}
        |
        |case class B(b: Int)
        |""".stripMargin
    extract(src, "m") mustBe Some(
      """  /** m
        |    * @param x the x
        |    */""".stripMargin)
    extract(src, "B") mustBe None
  }

  test("undocumented class after a documented val or statement") {
    extract("/** v */ val v = 1\nclass B", "B") mustBe None
    extract("/** v */ val v = 1; class B", "B") mustBe None
    extract("/** v */ println(1)\nclass B", "B") mustBe None
  }

  test("annotations and modifiers between the comment and the declaration, same line") {
    extract("/** x */ @deprecated final case class A(a: Int)", "A") mustBe Some("/** x */")
    extract("/** x */ @deprecated final case class A(a: Int)", "A", range = true) mustBe Some("/** x */")
  }

  test("modifiers") {
    extract("/** x */\nprivate[pkg] sealed abstract class A", "A") mustBe Some("/** x */")
    extract("/** x */\nprotected[this] implicit final class A(i: Int)", "A") mustBe Some("/** x */")
    extract("/** x */\nprivate case object A", "A") mustBe Some("/** x */")
    extract("/** x */\noverride lazy val a = 1", "a") mustBe Some("/** x */")
    extract("/** x */\nsealed trait A", "A") mustBe Some("/** x */")
    extract("/** x */\ntype A = Int", "A") mustBe Some("/** x */")
    // not a modifier: the comment belongs to something else
    extract("/** x */\nfoo\nclass A", "A") mustBe None
  }

  test("multi-line annotations with arguments") {
    val src =
      """  /** x
        |    * @param a the a
        |    */
        |  @marker(
        |    value = "a (tricky) ) value */ /** with markers",
        |    other = Seq('(', ')', '\'', '"'),
        |    third = s"${ "}" + 1 } $$ /** ",
        |    fourth = TQ" ) /** TQ"
        |  )
        |  @SerialVersionUID(
        |    1L
        |  ) @a.b.C[Int]() (2) @`odd name`
        |  final
        |  case class A(a: Int)
        |""".stripMargin.replace("TQ", "\"\"\"")
    val doc = Some(
      """  /** x
        |    * @param a the a
        |    */""".stripMargin)
    extract(src, "A") mustBe doc
    extract(src, "A", range = true) mustBe doc
  }

  test("doc comment inside annotation arguments") {
    extract("/** a */ @ann(/** b */ 1) class A", "A") mustBe Some("/** a */")
  }

  test("blank line between the comment and the declaration: still attached, as in scalac's scaladoc") {
    extract("/** x */\n\n\nclass A", "A") mustBe Some("/** x */")
  }

  test("line comment between the comment and the declaration: still attached, as in scalac and Scala 3") {
    extract("/** x */\n// a line comment\nclass A", "A") mustBe Some("/** x */")
    extract("/** x */\n@ann // a line comment\nclass A", "A") mustBe Some("/** x */")
  }

  test("block comment between the comment and the declaration: still attached, as in scalac and Scala 3") {
    extract("/** x */\n/* a block comment */\nclass A", "A") mustBe Some("/** x */")
    extract("/** x */\n/* nested /* block */ comment */\nclass A", "A") mustBe Some("/** x */")
    extract("/** x */\n/**/\nclass A", "A") mustBe Some("/** x */")
  }

  test("a doc comment inside a block comment is not a doc comment") {
    extract("/* outer /** inner */ still outer */\nclass A", "A") mustBe None
  }

  test("string literal containing a doc comment before the declaration") {
    extract("object S { val s = \"/** not a doc */\" }\nclass A", "A") mustBe None
    extract("object S { val s = \"\"\"\n/** not a doc */\n\"\"\" }\nclass A", "A") mustBe None
    extract("object S { val s = s\"${\"/** not */\"} /** not a doc */\" }\nclass A", "A") mustBe None
    extract("object S { val s = \"/** not a doc\" ; val t = \"*/\" }\nclass A", "A") mustBe None
  }

  test("first declaration in the file") {
    extract("class A", "A") mustBe None
    extract("package p\n\nclass A", "A") mustBe None
    extract("/** x */ class A", "A") mustBe Some("/** x */")
  }

  test("declaration inside a body, after other code") {
    extract("object O { val x = (1, \"/** no */\") ; { 2 }\n  /** d */\n  class A }", "A") mustBe Some("  /** d */")
    extract("object O { val x = (1, 2)\n  /** d */\n  class A }\nclass B", "B") mustBe None
  }

  test("last of several doc comments") {
    extract("/** x */\n/** y */\nclass A", "A") mustBe Some("/** y */")
  }

  test("a doc comment at the end of a body belongs to nothing") {
    extract("object O {\n  def f = 1\n  /** dangling */\n}\nclass A", "A") mustBe None
    extract("val xs = List(1 /** dangling */)\nclass A", "A") mustBe None
  }

  test("escaped quotes in string literals") {
    val src =
      """object O {
        |  /** Doc of x. */
        |  @deprecated("a \" quote, /** not a doc", "1.0") val x = 1; class A
        |}
        |""".stripMargin
    extract(src, "x") mustBe Some("  /** Doc of x. */")
    extract(src, "A") mustBe None
  }

  test("character literals with escapes, symbol literals") {
    // 'A' (written with a double backslash here, so that it stays an escape in the test source)
    extract("/** x */ @ann('\\u0041', '\\n', '\\\\', '\\'') class A", "A") mustBe Some("/** x */")
    extract("/** x */ @Tag('fast) @ann('sym, '\"', 'a') class A", "A") mustBe Some("/** x */")
    extract("object O { val s = Set('a, 'b) }\n/** x */\nclass A", "A") mustBe Some("/** x */")
    // the literals end where they end, so the annotation's `)` is found and `val x` after it detaches the comment
    extract("/** x */ @ann('\\u0041'+'\"') val x = 1; class A", "A") mustBe None
    extract("/** x */ @ann('s) val x = 1; class A", "A") mustBe None
  }

  test("code the lexer doesn't understand (XML literals) can't hide a later doc comment or make it loop") {
    // the `"` in the XML text opens a string literal for the lexer: it ends at the end of the line
    extract("object O { val q = <q>\"</q> }\n/** x */\nclass A", "A") mustBe Some("/** x */")
    // the `(` in the XML text opens a group that never closes: it ends at the end of the file
    extract("/** x */ @ann(<a>(</a>) class A", "A") mustBe Some("/** x */")
    // ... and a quote at the very end of the file, inside that group, is looked past safely
    extract("/** x */ @ann(<a>(</a>) class A(c: Char = '", "A") mustBe Some("/** x */")
  }
}

object SourceCodeUtilsSpec {

  private val DeclKeyword = "(?:class|object|trait|def|val|var|type)"

  /** `extractComment` for the declaration of `name` in `src`, at the position the macro gets: an offset position
    * at the name; with `range`, a range position from the first modifier or keyword (as scalac's parser sets it).
    */
  private def extract(src: String, name: String, range: Boolean = false): Option[String] = {
    val m = s"(?s).*?((?:(?:final|case|sealed|abstract|private|protected|implicit|override|lazy)\\s+)*)$DeclKeyword\\s+(\\Q$name\\E)\\b".r
      .findPrefixMatchOf(src)
      .getOrElse(throw new IllegalArgumentException(s"no declaration of $name in $src"))
    val s     = makeSrc(src)
    val point = m.start(2)
    val pos   = if (range) new RangePosition(s, m.start(1), point, point + name.length) else new OffsetPosition(s, point)
    SourceCodeUtils.extractComment(pos)
  }

  private def makePosNextLine(src: String): Position = {
    val s = makeSrc(src + "\n")
    new OffsetPosition(s, src.length)
  }

  private def makePosSameLine(src: String): Position = {
    val s = makeSrc(src + " ")
    new OffsetPosition(s, src.length)
  }

  private def makeSrc(src: String): SourceFile = {
    val file = new VirtualFile("test.scala", "/tmp")
    new BatchSourceFile(file, src.toCharArray)
  }
}