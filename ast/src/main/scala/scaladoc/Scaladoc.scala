package scaladoc

case class Scaladoc(tags: List[Tag]) extends Serializable

object Scaladoc {

  def apply(x: Tag, xs: Tag*): Scaladoc = Scaladoc(x +: xs.toList)
}