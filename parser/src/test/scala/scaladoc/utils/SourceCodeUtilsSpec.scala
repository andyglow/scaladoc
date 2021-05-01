package scaladoc.utils

import scaladoc._

import scala.reflect.internal.util.{BatchSourceFile, OffsetPosition, Position, SourceFile}
import scala.reflect.io.VirtualFile


class SourceCodeUtilsSpec extends FSpec {

  test("no indentation") {
    val pos = makePos(
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
    val pos = makePos(
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
    val pos = makePos(
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
    val pos = makePos(
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

  def makeSrc(src: String): SourceFile = {
    val file = new VirtualFile("test.scala", "/tmp")
    new BatchSourceFile(file, src.toCharArray)
  }
  def makePos(src: String): Position = {
    val s = makeSrc(src + "\n")
    new OffsetPosition(s, src.length)
  }
}
