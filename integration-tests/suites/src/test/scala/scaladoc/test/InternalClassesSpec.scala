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
        Param("a", Document(PlainText("A Param\n "))),
        Param("b", Document(PlainText("B Param"))))
    }
  }

  test("InternalClass2") {
    Scaladoc.of[InternalClass2] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("Internal Class 2")))),
        Param("c", Document(PlainText("C Param\n "))),
        Param("d", Document(PlainText("D Param"))))
    }
  }
}
