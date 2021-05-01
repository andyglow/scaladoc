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
}

object Scaladoc {

  def apply(x: Tag, xs: Tag*): Scaladoc = Scaladoc(x +: xs.toList)
}