package scaladoc.test.filtered.extra

/** Included by class name
  *
  * @param k the k
  */
case class KeptExtra(k: Int)

/** Not included
  *
  * @param d the d
  */
case class DroppedExtra(d: Int)
