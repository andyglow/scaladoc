package scaladoc.parser

import scaladoc.{Markup, Tag}

sealed trait MutableTag {
  def toTag: Tag
  def isOpen: Boolean
  def isBlank: Boolean
  def nonBlank: Boolean = !isBlank
}
object MutableTag {
  sealed trait HasMarkup { this: MutableTag =>
    def markup: Markup
    override def isBlank: Boolean = markup.isBlank
  }
  sealed trait HasValue { this: MutableTag =>
    def value: String
    override def isBlank: Boolean = value.isEmpty
  }
  final case class Author(var value: String)                          extends MutableTag with HasValue  { def toTag = Tag.Author(value)                  ; def isOpen = value eq null }
  final case class Version(var value: String)                         extends MutableTag with HasValue  { def toTag = Tag.Version(value)                 ; def isOpen = value eq null }
  final case class Since(var value: String)                           extends MutableTag with HasValue  { def toTag = Tag.Since(value)                   ; def isOpen = value eq null }
  final case class GroupName(id: String, var value: String)           extends MutableTag with HasValue  { def toTag = Tag.GroupName(id, value)           ; def isOpen = value eq null }
  final case class GroupDescription(id: String, var markup: Markup)   extends MutableTag with HasMarkup { def toTag = Tag.GroupDescription(id, markup)   ; def isOpen = markup eq null }
  final case class See(var markup: Markup)                            extends MutableTag with HasMarkup { def toTag = Tag.See(markup)                    ; def isOpen = markup eq null }
  final case class Constructor(var markup: Markup)                    extends MutableTag with HasMarkup { def toTag = Tag.Constructor(markup)            ; def isOpen = markup eq null }
  final case class Param(name: String, var markup: Markup)            extends MutableTag with HasMarkup { def toTag = Tag.Param(name, markup)            ; def isOpen = markup eq null }
  final case class TypeParam(name: String, var markup: Markup)        extends MutableTag with HasMarkup { def toTag = Tag.TypeParam(name, markup)        ; def isOpen = markup eq null }
  final case class Returns(var markup: Markup)                        extends MutableTag with HasMarkup { def toTag = Tag.Returns(markup)                ; def isOpen = markup eq null }
  final case class Throws(exceptionType: String, var markup: Markup)  extends MutableTag with HasMarkup { def toTag = Tag.Throws(exceptionType, markup)  ; def isOpen = markup eq null }
  final case class Note(var markup: Markup)                           extends MutableTag with HasMarkup { def toTag = Tag.Note(markup)                   ; def isOpen = markup eq null }
  final case class Example(var markup: Markup)                        extends MutableTag with HasMarkup { def toTag = Tag.Example(markup)                ; def isOpen = markup eq null }
  final case class UseCase(var markup: Markup)                        extends MutableTag with HasMarkup { def toTag = Tag.UseCase(markup)                ; def isOpen = markup eq null }
  final case class Todo(var markup: Markup)                           extends MutableTag with HasMarkup { def toTag = Tag.Todo(markup)                   ; def isOpen = markup eq null }
  final case class Deprecated(var markup: Markup)                     extends MutableTag with HasMarkup { def toTag = Tag.Deprecated(markup)             ; def isOpen = markup eq null }
  final case class Migration(var markup: Markup)                      extends MutableTag with HasMarkup { def toTag = Tag.Migration(markup)              ; def isOpen = markup eq null }
  final case class OtherTag(label: String, var markup: Markup)        extends MutableTag with HasMarkup { def toTag = Tag.OtherTag(label, markup)        ; def isOpen = markup eq null }
  final case class Description(var markup: Markup)                    extends MutableTag with HasMarkup { def toTag = Tag.Description(markup)            ; def isOpen = markup eq null }
  final case class GroupPriority(id: String, value: Int)              extends MutableTag                { def toTag = Tag.GroupPriority(id, value)       ; def isOpen = false; def isBlank = false }
  final case class Group(id: String)                                  extends MutableTag                { def toTag = Tag.Group(id)                      ; def isOpen = false; def isBlank = false }
  final case object Documentable                                      extends MutableTag                { def toTag = Tag.Documentable                   ; def isOpen = false; def isBlank = false }
  final case object InheritDoc                                        extends MutableTag                { def toTag = Tag.InheritDoc                     ; def isOpen = false; def isBlank = false }
}