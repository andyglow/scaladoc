package scaladoc.parser

import scaladoc._

class DefinesSpec extends FSpec {

  private def parse(x: String): Scaladoc = Scaladoc.fromString(x).fold(e => fail(e.getMessage), identity)

  test("a variable is its define's text, and the define goes") {
    val doc = parse(
      """/** A $coll of things, or a ${coll}s.
        |  *
        |  * @define coll list
        |  * @param a the $coll
        |  */""".stripMargin)
    doc.textDescriptions mustBe Seq("A list of things, or a lists.")
    doc.textParams mustBe Map("a" -> "the list")
    doc.tags.size mustBe 2
  }

  test("a define runs to the next tag and can use other defines") {
    Defines.expand("$a\n@define a ''the'' $b\n  more\n@define b end\n@param x $a") mustBe "''the'' end\n  more\n@param x ''the'' end\n  more"
  }

  test("escapes, unknown variables and a define that uses itself") {
    Defines.expand("\\$a, $b, ${a, $ and $a\n@define a x") mustBe "$a, $b, ${a, $ and x"
    Defines.expand("$a\n@define a <$a>") mustBe "<" * 11 + "$a" + ">" * 11
  }

  test("a comment without defines stays as it is") {
    Defines.expand("cost: \\$5 or $x") mustBe "cost: \\$5 or $x"
  }
}
