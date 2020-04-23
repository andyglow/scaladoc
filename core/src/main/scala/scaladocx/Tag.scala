package scaladocx


sealed trait Tag
object Tag {
  final case class Constructor(markup: Markup) extends Tag
  final case class Param(name: String, markup: Markup) extends Tag
  final case class TypeParam(name: String, markup: Markup) extends Tag
  final case class Returns(markup: Markup) extends Tag
  final case class Throws(exceptionType: String, markup: Markup) extends Tag
  final case class See(link: String) extends Tag
  final case class Note(markup: Markup) extends Tag
  final case class Example(markup: Markup) extends Tag
  final case class UseCase(markup: Markup) extends Tag
  final case class Author(text: String) extends Tag
  final case class Version(text: String) extends Tag
  final case class Since(text: String) extends Tag
  final case class Todo(markup: Markup) extends Tag
  final case class Deprecated(markup: Markup) extends Tag
  final case class Migration(markup: Markup) extends Tag
  final case class Group(id: String) extends Tag
  final case class GroupName(id: String, value: String) extends Tag
  final case class GroupDescription(id: String, value: String) extends Tag
  final case class GroupPriority(id: String, value: Int) extends Tag
  final case object Documentable extends Tag
  final case object InheritDoc extends Tag
  final case class OtherTag(label: String, markup: Markup) extends Tag
  final case class Description(makrup: Markup) extends Tag
}
