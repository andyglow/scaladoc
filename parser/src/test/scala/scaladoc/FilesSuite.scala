package scaladoc

import java.io.{File, FilenameFilter, StringWriter}
import java.nio.file.{FileSystem, Paths}

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import scaladoc.utils.{DumpAst, IdentWriter}

import scala.io.Source
import utils._


class FilesSuite extends AnyFunSuite {
  import FilesSuite._

  val files: List[TestFile] = {
    val testDir = new File("./parser/src/test/resources/tests")
    val ff = new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".test")
    }
    testDir.listFiles(ff).toList map TestFile.fromFile
  }

  files foreach { f =>
    test(if (f.ok) s"parse ${f.name} successfully" else s"fail on parsing ${f.name}") {
      f.test()
    }
  }
}

object FilesSuite {

  case class TestFile(name: String, ok: Boolean, source: String, expectation: String) {

    def test(): Unit = {
      Scaladoc.fromString(source) match {
        case Right(sdc) =>
//          println("***")
//          sdc.tags foreach { t => println(t); println("***") }


          val w = new StringWriter
          val iw = new IdentWriter(w)
          DumpAst(sdc, iw)
          val src = iw.toString
          val expLines = expectation.trim.linesIterator.toList

//          println(src)

          if (expLines.isEmpty) {
            fail("Expectation is not specified")
          } else {
            val srcLines = iw.toString.trim.linesIterator.toList
            srcLines.zip(expLines).zipWithIndex foreach { case ((l, r), i) =>
              withClue(s"line: $i") {
                l mustEqual r
              }
            }
          }
        case Left(err) => fail(err)
      }
    }
  }

  object TestFile {

    def fromString(name: String, ok: Boolean, content: String): TestFile = {
      content mustNot be( Symbol("empty") )
      val lines = content.linesIterator.toList.filterNot(_.isVain)
      lines mustNot be( Symbol("empty") )
      lines.head mustBe "-# --"

      val src = lines.tail.takeWhile(_ != "-# --").mkString("\n")
      val exp = lines.tail.dropWhile(_ != "-# --").drop(1).takeWhile(_ != "-# --").mkString("\n")

      TestFile(name, ok, src, exp)
    }

    def fromFile(x: File): TestFile = {
      val content = Source.fromFile(x).mkString
      TestFile.fromString(x.getName, x.getName.endsWith("-ok.test"), content)
    }
  }
}