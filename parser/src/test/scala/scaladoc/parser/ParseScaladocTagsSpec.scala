package scaladoc.parser

import scaladoc.FSpec


class ParseScaladocTagsSpec extends FSpec {

  test("one-liner description with one-liner params") {
    val doc = ParseScaladocTags(
      """My perfect class
        |
        |@param a "A" Param
        |@param b `B` Param
        |
        |""".stripMargin)

    doc.textDescriptions must contain only ("My perfect class")
    doc.textParams must contain only ("a" -> """"A" Param""", "b" -> """`B` Param""")
  }

  test("multiline plain description") {
    val doc = ParseScaladocTags(
      """Foo
        |Bar
        |Baz
        |""".stripMargin)

    doc.textDescriptions must contain only ("Foo\nBar\nBaz")
    doc.tags.size mustBe 1
  }

  test("multiline param") {
    val doc = ParseScaladocTags(
      """@param foo Foo
        |           Bar
        |           Baz
        |""".stripMargin)

    doc.textParams must contain only ("foo" -> "Foo\nBar\nBaz")
    doc.tags.size mustBe 1
  }

  test("extra trimmed") {
    val doc = ParseScaladocTags(
      """Multi
        |Line
        |Description
        |
        |@param a one line description
        |
        |@param b multi
        |         line
        |         description
        |
        |""".stripMargin)

    doc.textDescriptions must contain only ("Multi\nLine\nDescription")
    doc.textParams must contain only (
      "a" -> "one line description",
      "b" -> "multi\nline\ndescription")
  }
}
