package scaladoc.utils

trait IterableUtils {

  private[scaladoc] implicit class StringIterableOps(private val x: Iterable[String]) {

    def trim: Iterable[String] = {
      val redundantPrefixLen = x.foldLeft[Option[Int]](None) {
        case (None, str) => Some(str.leadingWS)
        case (Some(len), str) => Some(Math.min(len, str.leadingWS))
      }

      redundantPrefixLen match {
        case Some(n) => x map { _.drop(n)}
        case None    => x
      }
    }
  }
}
