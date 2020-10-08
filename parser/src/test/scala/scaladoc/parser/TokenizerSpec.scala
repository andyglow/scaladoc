package scaladoc.parser

import org.scalatest.matchers.must.Matchers._
import org.scalatest.funsuite.AnyFunSuite


class TokenizerSpec extends AnyFunSuite {
  import TokenizerSpec._

  test("isLineStart") {
    val t = Tok(
      """abc
        |  def
        |""".stripMargin)

    t.setPos(4)

    // spaces
    t.isLineStart mustBe true; t.char mustBe ' '; t.next()
    t.isLineStart mustBe true; t.char mustBe ' '; t.next()

    // first non WS char
    t.isLineStart mustBe true; t.char mustBe 'd'; t.next()
    t.isLineStart mustBe false; t.char mustBe 'e'
  }
}

object TokenizerSpec {

  final case class Tok(chars: Array[Char]) extends Tokenizer
  final object Tok {
    def apply(x: String): Tok = Tok(x.toCharArray)
  }
}
