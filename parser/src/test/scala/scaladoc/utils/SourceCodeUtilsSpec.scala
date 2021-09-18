package scaladoc.utils

import scaladoc._

import scala.reflect.internal.util.{BatchSourceFile, OffsetPosition, Position, SourceFile}
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



}

object SourceCodeUtilsSpec {

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