package scaladoc

sealed trait Style

object Style {

  sealed trait NonScaladoc extends Style

  case object Stripped extends Style

  case object Scaladoc extends Style

  case object Block extends NonScaladoc

  case object Line extends NonScaladoc
}