package scaladoc.ofmodels

import scaladoc._

// Models whose comments use the defines of their parents and enclosing classes. They're in this file, with the spec,
// for the reason `OfSpec`'s are: Scala 2 reads a comment from the source text, only there when it's in the same run.

/** Things in general.
  *
  * @define coll collection
  * @define thing a $coll of things
  * @define origin from the base
  */
trait DefinesBase

/** This is $thing, $origin.
  *
  * @define coll list
  * @param x the first of the $coll
  */
case class DefinesChild(x: Int) extends DefinesBase

/** A middle.
  *
  * @define coll sequence
  */
trait DefinesMiddle extends DefinesBase

/** This is $thing. */
class DefinesLeaf extends DefinesMiddle

/** The outer object.
  *
  * @define where in the outer object
  */
object DefinesOuter {

  /** Defined $where, $origin, and $nowhere stays. */
  class Inner extends DefinesBase
}

/** A self type.
  *
  * @define selfish from the self type
  */
trait DefinesSelf

/** Uses a define $selfish. */
trait DefinesWithSelf { self: DefinesSelf => }

// `Scaladoc.of` expands the defines of a comment's parents and enclosing classes, as scaladoc does
class OfDefinesSpec extends scaladoc.FSpec {

  private def description(doc: Option[Scaladoc]): String = doc.map(_.textDescriptions.mkString).getOrElse("")

  test("a parent's defines, which the class's own override, wherever they're used") {
    val doc = Scaladoc.of[DefinesChild]
    description(doc) mustBe "This is a list of things, from the base."
    doc.map(_.textParams) mustBe Some(Map("x" -> "the first of the list"))
  }

  test("base classes in linearization order: the nearest parent's define wins") {
    description(Scaladoc.of[DefinesLeaf]) mustBe "This is a sequence of things."
  }

  test("the enclosing object's defines, and its parents', and an unknown variable stays") {
    description(Scaladoc.of[DefinesOuter.Inner]) mustBe "Defined in the outer object, from the base, and $nowhere stays."
  }

  test("the self type's defines") {
    description(Scaladoc.of[DefinesWithSelf]) mustBe "Uses a define from the self type."
  }

  test("a comment's own defines are as before") {
    description(Scaladoc.of[DefinesBase]) mustBe "Things in general."
  }
}
