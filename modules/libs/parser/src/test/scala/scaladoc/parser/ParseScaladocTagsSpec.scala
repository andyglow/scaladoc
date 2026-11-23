package scaladoc.parser

import scaladoc.FSpec


class ParseScaladocTagsSpec extends FSpec {

  private def text(x: String) = scaladoc.Markup.Document(scaladoc.Markup.Paragraph(scaladoc.Markup.PlainText(x)))

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
        |@param b_b underscore in the param name
        |
        |@param c multi
        |         line
        |         description
        |
        |""".stripMargin)

    doc.textDescriptions must contain only ("Multi\nLine\nDescription")
    doc.textParams must contain only (
      "a" -> "one line description",
      "b_b" -> "underscore in the param name",
      "c" -> "multi\nline\ndescription")
  }

  test("@return and @returns") {
    ParseScaladocTags("@return the result").textReturns mustBe Some("the result")
    ParseScaladocTags("@returns the result").textReturns mustBe Some("the result")
  }

  test("an @ inside a line is text") {
    val doc = ParseScaladocTags(
      """Write to foo@bar.com, mark it @tailrec
        |or see {@link Foo}
        |
        |@param a mail a@b.c
        |""".stripMargin)

    // `{@link Foo}` is a link, not a tag
    doc.textDescriptions must contain only ("Write to foo@bar.com, mark it @tailrec\nor see [[Foo]]")
    doc.textParams must contain only ("a" -> "mail a@b.c")
  }

  test("a tag can be indented") {
    ParseScaladocTags("Desc\n  @param a A").textParams must contain only ("a" -> "A")
  }

  test("no tags inside a code block") {
    val doc = ParseScaladocTags(
      """Desc
        |
        |@example {{{
        |@main def run(): Unit = ()
        |@param x not a tag
        |}}}
        |@param a A
        |""".stripMargin)

    doc.textParams must contain only ("a" -> "A")
    val codeBlocks = doc.tags.collect { case scaladoc.Tag.Example(scaladoc.Markup.Document(els)) =>
      els.collect { case scaladoc.Markup.CodeBlock(code, _) => code }
    }
    codeBlocks must contain only (List("@main def run(): Unit = ()\n@param x not a tag"))
  }

  test("@throws with a fully qualified exception type") {
    val doc = ParseScaladocTags("@throws java.io.IOException if it can't read\n@throws IllegalStateException never")
    doc.tags mustBe List(
      scaladoc.Tag.Throws("java.io.IOException", text("if it can't read")),
      scaladoc.Tag.Throws("IllegalStateException", text("never")))
  }

  test("@deprecated and @throws without text are kept") {
    val doc = ParseScaladocTags("Desc\n\n@deprecated\n@throws java.io.IOException\n@note")
    doc.tags.drop(1) mustBe List(
      scaladoc.Tag.Deprecated(scaladoc.Markup.Document(Nil)),
      scaladoc.Tag.Throws("java.io.IOException", scaladoc.Markup.Document(Nil)))
  }

  test("text of a tag keeps the spacing around inline markup") {
    ParseScaladocTags("@param a an `a@b` value").textParams must contain only ("a" -> "an `a@b` value")
  }

  test("no tags inside <pre>") {
    val doc = ParseScaladocTags("Desc\n<PRE>\n@Override\npublic void run()\n</pre>\n@param a A")
    doc.textParams must contain only ("a" -> "A")
    doc.tags.size mustBe 2
  }
  test("summary of a parsed comment") {
    ScaladocParser.fromString("/** = Users =\n  *\n  * Keeps the '''users''' of the app. All of them.\n  */")
      .map(_.textSummary) mustBe Right(Some("Keeps the '''users''' of the app."))
    ScaladocParser.fromString("/** Long text.\n  * @shortDescription Short.\n  */").map(_.textSummary) mustBe Right(Some("Short."))
  }
}
