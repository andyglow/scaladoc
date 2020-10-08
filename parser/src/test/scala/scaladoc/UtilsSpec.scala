package scaladoc

import org.scalatest.matchers.must.Matchers._
import org.scalatest.funsuite.AnyFunSuite
import utils._


class UtilsSpec extends AnyFunSuite {

  test("trim") {
    List(" abc", " def").trim mustBe List("abc", "def")
    List(" abc", "  def").trim mustBe List("abc", " def")
  }

  test("firstParSep") {
    "".firstParSep mustBe None
    "123".firstParSep mustBe None
    "123\n".firstParSep mustBe None
    "123\n\n456".firstParSep mustBe Some((3, 2))
    "123\n123\n456".firstParSep mustBe None
    "123\n   \n456".firstParSep mustBe Some((3, 5))
    "123\n   \n  \n456".firstParSep mustBe Some((3, 8))
    s"123\u2029456".firstParSep mustBe Some((3, 1))
    s"123\u2029456\n \n789".firstParSep mustBe Some((3, 1))
  }

  test("splitByParSep") {
    "".splitByParSep mustBe List("")
    "123".splitByParSep mustBe List("123")
    "123\n".splitByParSep mustBe List("123\n")
    "123\n\n456".splitByParSep mustBe List("123", "456")
    "123\n123\n456".splitByParSep mustBe List("123\n123\n456")
    "123\n   \n456".splitByParSep mustBe List("123", "456")
    "123\u2029456".splitByParSep mustBe List("123", "456")
    "123\u2029456\n \n789".splitByParSep mustBe List("123", "456", "789")
    "123\u2029456\n \n789\n\n\n \n0".splitByParSep mustBe List("123", "456", "789", "0")
  }

  test("allParSeps") {
    "".allParSeps mustBe Nil
    "123".allParSeps mustBe Nil
    "123\n".allParSeps mustBe Nil
    "123\n\n".allParSeps mustBe List((3, 2))
    "123\n\n456".allParSeps mustBe List((3, 2))
    "123\n123\n456".allParSeps mustBe Nil
    "123\n   \n456".allParSeps mustBe List((3, 5))
    s"123\u2029456".allParSeps mustBe List((3, 1))
    s"123\u2029456\n \n789".allParSeps mustBe List((3, 1), (7, 3))
    s"123\u2029456\n \n789\n\n\n \n0".allParSeps mustBe List((3, 1), (7, 3), (13, 5))
  }
}
