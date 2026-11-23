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
        Param("a", Document(Paragraph(PlainText("A Param")))),
        Param("b", Document(Paragraph(PlainText("B Param")))))
    }
  }

  test("ExternalClass2") {
    Scaladoc.of[ExternalClass2] mustBe Some {
      Scaladoc(
        Description(Document(Paragraph(PlainText("External Class 2")))),
        Param("c", Document(Paragraph(PlainText("C Param")))),
        Param("d", Document(Paragraph(PlainText("D Param")))))
    }
  }

  test("class nested in a class") {
    Scaladoc.of[ExternalOuter#Inner] mustBe Some {
      Scaladoc(Description(Document(Paragraph(PlainText("Nested in a class")))), Param("b", Document(Paragraph(PlainText("B Param")))))
    }
  }

  test("class nested in a trait") {
    Scaladoc.of[ExternalTrait#Member] mustBe Some {
      Scaladoc(Description(Document(Paragraph(PlainText("Nested in a trait")))), Param("c", Document(Paragraph(PlainText("C Param")))))
    }
  }

  test("class nested two levels deep") {
    Scaladoc.of[ExternalTrait#Member#Deeper] mustBe Some {
      Scaladoc(Description(Document(Paragraph(PlainText("Two levels deep")))), Param("d", Document(Paragraph(PlainText("D Param")))))
    }
  }
}
