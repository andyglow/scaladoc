package scaladoc.test

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers._
import scaladoc.Markup._
import scaladoc.Tag._
import scaladoc._
import scaladoc.test.external._


class ExternalClassesSpec extends AnyFunSuite {

  test("ExternalClass1") {
    Scaladoc.of[ExternalClass1] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("External Class 1")))),
        Param("a", Document(PlainText("A Param\n "))),
        Param("b", Document(PlainText("B Param"))))
    }
  }

  test("ExternalClass2") {
    Scaladoc.of[ExternalClass2] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("External Class 2")))),
        Param("c", Document(PlainText("C Param\n "))),
        Param("d", Document(PlainText("D Param"))))
    }
  }
}
