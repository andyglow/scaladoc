package scaladocx.utils


object ParSep {

  sealed trait Token extends Any
  final case class Text(value: String) extends AnyVal with Token
  final case object Separator extends Token

}
