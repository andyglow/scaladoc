package scaladoc

case class Scaladoc(tags: List[Tag]) extends Serializable {

  def descriptions: Seq[Markup] = tags.collect { case Tag.Description(x) => x }

  def textDescriptions: Seq[String] = descriptions map { _.trimmed.plainString }

  def params: Map[String, Markup] = tags.collect { case Tag.Param(name, x) => (name, x) }.toMap

  def textParams: Map[String, String] = params.map { case (k, v) => (k, v.trimmed.plainString) }

  def typeParams: Map[String, Markup] = tags.collect { case Tag.TypeParam(name, x) => (name, x) }.toMap

  def textTypeParams: Map[String, String] = typeParams.map { case (k, v) => (k, v.trimmed.plainString) }

  def `throws`: Map[String, Markup] = tags.collect { case Tag.Throws(name, x) => (name, x) }.toMap

  def textThrows: Map[String, String] = `throws`.map { case (k, v) => (k, v.trimmed.plainString) }

  def returns: Option[Markup] = tags.collectFirst { case Tag.Returns(x) => x }

  def textReturns: Option[String] = returns.map { _.trimmed.plainString }

  /** What scaladoc shows in member lists: the first sentence of the description, that is its first paragraph up to
    * and with the first `.` followed by whitespace or the end, or the `@shortDescription` if there is one. A `.` in
    * markup, like `'''v1.2. done'''`, doesn't end the sentence.
    */
  def summary: Option[Markup] =
    tags.collectFirst { case Tag.OtherTag("shortDescription", x) => x } orElse
      descriptions.iterator.flatMap(Scaladoc.firstParagraph).map(Scaladoc.firstSentence).toSeq.headOption

  def textSummary: Option[String] = summary.map { _.trimmed.plainString }
}

object Scaladoc {

  private def firstParagraph(x: Markup): Option[Markup.Paragraph] = x match {
    case p: Markup.Paragraph    => Some(p)
    case Markup.Document(elems) => elems.iterator.flatMap(firstParagraph).toSeq.headOption
    case _                      => None
  }

  private def firstSentence(p: Markup.Paragraph): Markup.Paragraph = {
    def end(text: String): Option[Int] =
      text.indices.find(i => text.charAt(i) == '.' && (i + 1 == text.length || text.charAt(i + 1).isWhitespace))
    val (before, rest) = p.markup.span {
      case Markup.PlainText(text) => end(text).isEmpty
      case _                      => true
    }
    rest match {
      case Markup.PlainText(text) :: _ => Markup.Paragraph(before :+ Markup.PlainText(text.take(end(text).get + 1)))
      case _                           => p
    }
  }

  def apply(x: Tag, xs: Tag*): Scaladoc = Scaladoc(x +: xs.toList)
}