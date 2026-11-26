package demo.model

/** A subscription plan. */
sealed trait Plan

object Plan {

  /** Free, for everyone. */
  case object Free extends Plan

  /** Paid by the month.
    *
    * @param price the price
    */
  final case class Monthly(price: Int) extends Plan

  /** Paid once. */
  case object Lifetime extends Plan
}
