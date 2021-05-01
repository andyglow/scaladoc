package scaladoc.utils

import org.scalatest.matchers.should.Matchers._
import org.scalatest.funsuite.AnyFunSuite
import scaladoc.Scaladoc

import scala.reflect.internal.util.{BatchSourceFile, OffsetPosition, Position, SourceFile}
import scala.reflect.io.VirtualFile


class SourceCodeUtilsSpec extends AnyFunSuite {

  test("no indentation") {
    val pos = makePos(
      s"""/** foo
         |  *
         |  */
         |""".stripMargin)
    SourceCodeUtils.extractComment(pos) shouldBe Some(
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
    SourceCodeUtils.extractComment(pos) shouldBe Some(
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
    SourceCodeUtils.extractComment(pos) shouldBe Some(
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
    SourceCodeUtils.extractComment(pos) shouldBe Some(
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
