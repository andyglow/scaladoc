package scaladoc

// the errors the parser still reports, with the messages users see, e.g. in a `Scaladoc.of` compile error
class ExceptionsSpec extends FSpec {

  private def error(x: String, strict: Boolean = false): ScaladocException =
    Scaladoc.fromString(x, strict = strict).fold(identity, doc => fail(s"parsed: $doc"))

  test("no comment") {
    error("no comment here") mustBe EmptyInput
  }

  test("not a scaladoc") {
    error("// line comment") mustBe NonScaladocStyle(Style.Line)
    error("/* block comment */") mustBe NonScaladocStyle(Style.Block)
  }

  test("not a comment marker") {
    val e = error("/ x")
    e mustBe a[UnexpectedChar]
    e.getMessage mustBe "Position: 1 (0:1). Unexpected char: ' '. Expected: one of '/', '*'"
  }

  test("a comment marker cut off") {
    error("/").getMessage mustBe "Position: 1 (0:0). Unexpected EOF. Expected: one of '/', '*'"
  }

  test("too many stars") {
    error("/***x */").getMessage must endWith("Unexpected char: '*'. Expected: anything but '*'")
  }

  test("unclosed comment") {
    val e = error("/** abc\n  * def")
    e mustBe a[UnexpectedEOF]
    e.getMessage must endWith("Unexpected EOF")
  }

  test("misaligned border, strict") {
    val e = error("/** abc\n   * def\n  */", strict = true)
    e mustBe a[BrokenIndentation]
    e.getMessage must endWith("Broken indentation. Actual: 3. Expected: 1")
  }

  test("group priority without a number") {
    val e = error("/** @groupprio g1 */")
    e mustBe a[EmptyGroupPriority]
    e.getMessage must endWith("Group Priority is not specified")
  }
}
