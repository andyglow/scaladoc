package scaladoc.test

import org.scalatest.matchers.must.Matchers._
import org.scalatest.funsuite.AnyFunSuite
import scaladoc._
import scaladoc.Tag._
import scaladoc.Markup._


class InternalClassesSpec extends AnyFunSuite {

  test("InternalClass1") {
    Scaladoc.of[InternalClass1] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("Internal Class 1")))),
        Param("a", Document(Paragraph(PlainText("A Param")))),
        Param("b", Document(Paragraph(PlainText("B Param")))))
    }
  }

  test("InternalClass2") {
    Scaladoc.of[InternalClass2] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("Internal Class 2")))),
        Param("c", Document(Paragraph(PlainText("C Param")))),
        Param("d", Document(Paragraph(PlainText("D Param")))))
    }
  }
}
