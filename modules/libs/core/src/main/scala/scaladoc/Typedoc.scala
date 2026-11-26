package scaladoc

/** A type's docs as a tree, as `Typedoc.of[T]` gives them: its own scaladoc, and for a sealed hierarchy or an enum
  * the docs of its variants, for a case class those of its fields.
  *
  * Variants recurse, since a sealed hierarchy is finite: a variant that's sealed itself is a nested [[Typedoc.Sum]].
  * Fields don't: a field has its own doc, from its `@param`, not its type's tree.
  */
sealed trait Typedoc extends scala.Product with Serializable {

  /** The type's simple name, like `Circle`, or the enum case's, like `Red`. */
  def name: String

  /** The type's scaladoc, as `Scaladoc.of` gives it. */
  def doc: Option[Scaladoc]
}

object Typedoc {

  /** A sealed trait or class, or a Scala 3 enum, with its direct children as variants, in declaration order where
    * the compiler knows it, and by name otherwise (Scala 2 classes from a jar).
    */
  final case class Sum(name: String, doc: Option[Scaladoc], variants: List[Typedoc]) extends Typedoc

  /** A case class, or a Scala 3 enum case with parameters, with the first parameter list of its constructor as
    * fields, in order.
    */
  final case class Product(name: String, doc: Option[Scaladoc], fields: List[Field]) extends Typedoc

  /** An object, or a Scala 3 enum case without parameters. */
  final case class Singleton(name: String, doc: Option[Scaladoc]) extends Typedoc

  /** Any other type. */
  final case class Plain(name: String, doc: Option[Scaladoc]) extends Typedoc

  /** A field of a [[Product]], with its doc: the `@param` of its name in the product's scaladoc. */
  final case class Field(name: String, doc: Option[Markup])
}
