package scaladocx.parse

import scaladocx.{Markup, Tag}

sealed trait MutableTag {
  def toTag: Tag
  def isOpen: Boolean
}
object MutableTag {
  final case class Constructor(var markup: Markup)                    extends MutableTag { def toTag = Tag.Constructor(markup)            ; def isOpen = markup eq null }
  final case class Param(name: String, var markup: Markup)            extends MutableTag { def toTag = Tag.Param(name, markup)            ; def isOpen = markup eq null }
  final case class TypeParam(name: String, var markup: Markup)        extends MutableTag { def toTag = Tag.TypeParam(name, markup)        ; def isOpen = markup eq null }
  final case class Returns(var markup: Markup)                        extends MutableTag { def toTag = Tag.Returns(markup)                ; def isOpen = markup eq null }
  final case class Throws(exceptionType: String, var markup: Markup)  extends MutableTag { def toTag = Tag.Throws(exceptionType, markup)  ; def isOpen = markup eq null }
  final case class See(var link: String)                              extends MutableTag { def toTag = Tag.See(link)                      ; def isOpen = link eq null }
  final case class Note(var markup: Markup)                           extends MutableTag { def toTag = Tag.Note(markup)                   ; def isOpen = markup eq null }
  final case class Example(var markup: Markup)                        extends MutableTag { def toTag = Tag.Example(markup)                ; def isOpen = markup eq null }
  final case class UseCase(var markup: Markup)                        extends MutableTag { def toTag = Tag.UseCase(markup)                ; def isOpen = markup eq null }
  final case class Author(var text: String)                           extends MutableTag { def toTag = Tag.Author(text)                   ; def isOpen = text eq null }
  final case class Version(var text: String)                          extends MutableTag { def toTag = Tag.Version(text)                  ; def isOpen = text eq null }
  final case class Since(var text: String)                            extends MutableTag { def toTag = Tag.Since(text)                    ; def isOpen = text eq null }
  final case class Todo(var markup: Markup)                           extends MutableTag { def toTag = Tag.Todo(markup)                   ; def isOpen = markup eq null }
  final case class Deprecated(var markup: Markup)                     extends MutableTag { def toTag = Tag.Deprecated(markup)             ; def isOpen = markup eq null }
  final case class Migration(var markup: Markup)                      extends MutableTag { def toTag = Tag.Migration(markup)              ; def isOpen = markup eq null }
  final case class Group(id: String)                                  extends MutableTag { def toTag = Tag.Group(id)                      ; def isOpen = false }
  final case class GroupName(id: String, value: String)               extends MutableTag { def toTag = Tag.GroupName(id, value)           ; def isOpen = false }
  final case class GroupDescription(id: String, var value: String)    extends MutableTag { def toTag = Tag.GroupDescription(id, value)    ; def isOpen = value eq null }
  final case class GroupPriority(id: String, value: Int)              extends MutableTag { def toTag = Tag.GroupPriority(id, value)       ; def isOpen = false }
  final case object Documentable                                      extends MutableTag { def toTag = Tag.Documentable                   ; def isOpen = false }
  final case object InheritDoc                                        extends MutableTag { def toTag = Tag.InheritDoc                     ; def isOpen = false }
  final case class OtherTag(label: String, var markup: Markup)        extends MutableTag { def toTag = Tag.OtherTag(label, markup)        ; def isOpen = markup eq null }
  final case class Description(var markup: Markup)                    extends MutableTag { def toTag = Tag.Description(markup)            ; def isOpen = markup eq null }
}