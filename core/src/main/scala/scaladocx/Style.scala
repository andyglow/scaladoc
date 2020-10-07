package scaladocx

sealed trait Style

object Style {

  sealed trait NonScaladoc extends Style

  final case object Stripped extends Style

  final case object Scaladoc extends Style

  final case object Block extends NonScaladoc

  final case object Line extends NonScaladoc
}