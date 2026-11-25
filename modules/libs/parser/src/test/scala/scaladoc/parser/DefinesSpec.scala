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

  test("a comment without defines: an escaped dollar is a dollar all the same, and unknown variables stay") {
    Defines.expand("cost: \\$5 or $x") mustBe "cost: $5 or $x"
    Defines.expand("cost: \\$5 or $x", Map("y" -> "z")) mustBe "cost: $5 or $x"
    Defines.expand("no variables") mustBe "no variables"
  }

  test("what is and isn't a define line") {
    Defines.expand("$a $b $c\n   @define a indented\n@defined b no\n@define\n@define  c   spaced  ") mustBe
      "indented $b spaced\n@defined b no\n@define"
    Defines.of("@define a one\n@define a two") mustBe Map("a" -> "two")
    Defines.of("@define -a not a name\n@define ") mustBe Map.empty
    Defines.of("text\n@define a x\n  more\n@param p y") mustBe Map("a" -> "x\n  more")
  }

  test("outer defines: the comment's own come first, and are expanded where they're used") {
    val outer = Map("coll" -> "collection", "thing" -> "a $coll of things")
    Defines.expand("$thing\n@define coll list", outer) mustBe "a list of things"
    Defines.expand("$thing", outer) mustBe "a collection of things"
    Defines.expand("no variables", outer) mustBe "no variables"
  }

  test("defines in lookup order: the first one with a name wins") {
    Defines.inOrder(Seq(Map("a" -> "1"), Map("a" -> "2", "b" -> "2"), Map("b" -> "3", "c" -> "3"))) mustBe
      Map("a" -> "1", "b" -> "2", "c" -> "3")
    Defines.inOrder(Nil) mustBe Map.empty
  }

  test("a comment's defines, for the classes it's in or extends") {
    ScaladocParser.defines("/** Text.\n  *\n  * @define coll list\n  */", hasTags = true) mustBe Map("coll" -> "list")
    ScaladocParser.defines("Text.\n@define coll list", hasTags = false) mustBe Map("coll" -> "list")
    ScaladocParser.defines("/* not scaladoc @define coll list */", hasTags = true) mustBe Map.empty
    ScaladocParser.defines("not a comment", hasTags = true) mustBe Map.empty
  }
}
