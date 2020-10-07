package scaladocs.compile

import scala.tools.nsc._

trait CrossVersionSupport {
  val global: Global
  import global._


  def reportError(pos: Position, message: String, sym: Symbol): Unit = {
    runReporting.warning(pos, message, Reporting.WarningCategory.Scaladoc, sym)
  }
}
