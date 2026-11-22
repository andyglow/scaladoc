package scaladoc

import scaladoc.Markup._

class SummarySpec extends FSpec {

  private def doc(elements: Markup*) = Scaladoc(Tag.Description(Document(elements.toList)))

  test("the first sentence of the first paragraph") {
    doc(Paragraph(PlainText("First one. Second one."))).textSummary mustBe Some("First one.")
    doc(Heading(1, "Title"), CodeBlock("x"), Paragraph(PlainText("Text. More")), Paragraph(PlainText("Next."))).textSummary mustBe
      Some("Text.")
    doc(Paragraph(PlainText("No end"))).textSummary mustBe Some("No end")
    doc(Paragraph(PlainText("Ends with a dot."))).textSummary mustBe Some("Ends with a dot.")
    doc(Paragraph(PlainText("Version 1.2 is out. Yes"))).textSummary mustBe Some("Version 1.2 is out.")
    doc(Paragraph(PlainText("Line one.\nLine two."))).textSummary mustBe Some("Line one.")
  }

  test("a dot in markup doesn't end the sentence") {
    doc(Paragraph(PlainText("Uses "), Bold("v1. two"), PlainText(" now. Then"))).summary mustBe
      Some(Paragraph(PlainText("Uses "), Bold("v1. two"), PlainText(" now.")))
  }

  test("@shortDescription wins, and no description is no summary") {
    Scaladoc(List(Tag.Description(Paragraph(PlainText("Long. Text"))), Tag.OtherTag("shortDescription", PlainText("Short"))))
      .textSummary mustBe Some("Short")
    Scaladoc(List(Tag.Param("a", PlainText("A")))).summary mustBe None
    doc(CodeBlock("only code")).summary mustBe None
  }
}
