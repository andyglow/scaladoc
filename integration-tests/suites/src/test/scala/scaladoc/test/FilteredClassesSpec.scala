package scaladoc.test

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers._
import scaladoc.Markup._
import scaladoc.Tag._
import scaladoc._
import scaladoc.test.filtered.extra._
import scaladoc.test.filtered.model._
import scaladoc.test.filtered.model.internal._

// `filtered-models` is compiled with
//   include:path:**/filtered/model/**
//   include:class:scaladoc.test.filtered.extra.Kept*
//   exclude:path:**/Skipped.scala
//   exclude:class:scaladoc.test.filtered.**.internal.*
// Its classes come from a jar without sources, so `Scaladoc.of` finds a scaladoc only if the plugin embedded it.
class FilteredClassesSpec extends AnyFunSuite {

  private def doc(description: String, param: String, paramDoc: String) = Some {
    Scaladoc(Description(Document(Paragraph(PlainText(description)))), Param(param, Document(PlainText(paramDoc))))
  }

  test("included by path") {
    Scaladoc.of[User] mustBe doc("A user", "name", "the name")
  }

  test("included by path, nested in an object") {
    Scaladoc.of[Holder.Nested] mustBe doc("Nested in an object", "x", "the x")
  }

  test("included by class name") {
    Scaladoc.of[KeptExtra] mustBe doc("Included by class name", "k", "the k")
  }

  test("not included") {
    Scaladoc.of[DroppedExtra] mustBe None
  }

  test("excluded by path") {
    Scaladoc.of[Skipped] mustBe None
  }

  test("excluded by class name") {
    Scaladoc.of[Secret] mustBe None
  }
}
