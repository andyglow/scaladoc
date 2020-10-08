package scaladoc.parser

import org.scalactic.source.Position
import org.scalatest.matchers.must.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import scaladoc.parser.StripCommentTags.Comment
import scaladoc._
import scaladoc.utils._


class StripCommentTagsSpec extends AnyWordSpec {
  import Style._

  private def strip(expectedStyle: Style, x: String, strict: Boolean = false)(implicit pos: Position): String = {
    StripCommentTags(x, strict) match {
      case Some(Comment(style, body)) =>
        style mustBe expectedStyle
        body
      case None =>
        fail("Unexpected empty result")
    }
  }

  "StripCommentTags" should {

    "handle single line comments" when {

      "oneliners" when {

        "empty" in {
          strip(Line,"//") mustBe EmptyString
          strip(Line," //") mustBe EmptyString
          strip(Line," \t//") mustBe EmptyString
          strip(Line,"\n\t\n //") mustBe EmptyString
        }

        "non-empty" in {
          strip(Line, "//abc") mustBe "abc"
          strip(Line, "// abc") mustBe "abc"
          strip(Line, "//\tabc") mustBe "abc"
        }
      }

      "multi-line" when {

        "for empty cases" in {
          strip(Line,"//\n//") mustBe EmptyString
          strip(Line," //\n //\n") mustBe "\n"
          strip(Line," \t//\n \t//") mustBe EmptyString
          strip(Line,"\n\t\n //\n\n\t\n //\t\n") mustBe EmptyString
        }

        "for non-empty cases" in {
          strip(Line,"//abc\n//def") mustBe "abc\ndef"
          strip(Line," // abc\n // def\n") mustBe "abc\ndef"
          strip(Line," // abc\n //  def\n") mustBe "abc\n def"
          strip(Line," \t//   abc\n \t//def") mustBe "   abc\ndef"
          strip(Line,"\n\t\n // abc\n\t // def\t\n") mustBe "abc\ndef\t"
        }

        "for cases with broken indentation" in {
          // negative
          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Line,
              """   // abc
                |  // def
                |""".stripMargin,
              strict = true)
          }

          // positive
          strip(
            Line,
            """   // abc
              |  // def
              |""".stripMargin) mustBe "abc\ndef"
        }
      }

      "even if there are gaps" in {
        strip(Line,"\n\t\n // abc\n\n\t\n // def\t\n") mustBe "abc"
      }
    }

    "handle block comments" when {

      "oneliners" when {

        "empty" in {
          strip(Block, "/**/") mustBe EmptyString
          strip(Block, " /* */") mustBe EmptyString
          strip(Block, " \t/*\t */\t ") mustBe EmptyString
          strip(Block, "\n\t\n /**/") mustBe EmptyString
        }

        "non-empty" in {
          strip(Block, "/*abc*/") mustBe "abc"
          strip(Block, " /* abc*/") mustBe "abc"
          strip(Block, " \t/*\ta\tb\tc */\t ") mustBe "a\tb\tc "
          strip(Block, "\n\t\n /*abc */") mustBe "abc "
        }
      }

      "multi-line" when {

        "for empty cases" in {
          strip(
            Block,
            """/*
              | *
              | */
              |""".stripMargin) mustBe "\n"

          strip(
            Block,
            """  /*
              |   *
              |   */
              |""".stripMargin) mustBe "\n"

          strip(
            Block,
            """ /*
              |  *
              |  */
              |""".stripMargin) mustBe "\n"

          strip(
            Block,
            """ /*
              |  *
              |  *
              |  */
              |""".stripMargin) mustBe "\n\n"
        }

        "for non-empty cases" in {
          strip(
            Block,
            """/*abc
              | *def
              | */
              |""".stripMargin) mustBe "abc\ndef"

          strip(
            Block,
            """ /*abc
              |  *  def
              |  */
              |""".stripMargin) mustBe "abc\n  def"

          strip(
            Block,
            """  /*abc
              |   *def
              |   */
              |""".stripMargin) mustBe "abc\ndef"
        }

        "for cases with broken indentation" in {
          // negative
          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Block,
              """  /* abc
                |   * def
                |  */
                |""".stripMargin,
              strict = true)
          }

          // positive
          strip(
            Block,
            """  /* abc
              |   * def
              |  */
              |""".stripMargin) mustBe "abc\ndef"
        }
      }
    }

    "handle scaladoc" when {

      "oneliners" when {

        "for empty cases" in {
          strip(Scaladoc, "/***/") mustBe EmptyString
          strip(Scaladoc, " /** */") mustBe EmptyString
          strip(Scaladoc, " \t/**\t */\t ") mustBe EmptyString
          strip(Scaladoc, "\n\t\n /***/") mustBe EmptyString
        }

        "for non-empty cases" in {
          strip(Scaladoc, "/**abc*/") mustBe "abc"
          strip(Scaladoc, " /** abc*/") mustBe "abc"
          strip(Scaladoc, " \t/**\ta\tb\tc */\t ") mustBe "a\tb\tc "
          strip(Scaladoc, "\n\t\n /**abc */") mustBe "abc "
        }
      }

      "multi-line" when {

        "for empty cases" in {
          strip(
            Scaladoc,
            """/**
              | *
              | */
              |""".stripMargin) mustBe "\n"

          strip(
            Scaladoc,
            """  /**
              |   *
              |   */
              |""".stripMargin) mustBe "\n"

          strip(
            Scaladoc,
            """ /**
              |  *
              |  */
              |""".stripMargin) mustBe "\n"

          strip(
            Scaladoc,
            """ /**
              |  *
              |  *
              |  */
              |""".stripMargin) mustBe "\n\n"
        }

        "for non-empty cases" in {
          strip(
            Scaladoc,
            """/**abc
              | *def
              | */
              |""".stripMargin) mustBe "abc\ndef"

          strip(
            Scaladoc,
            """ /**abc
              |  *  def
              |  */
              |""".stripMargin) mustBe "abc\n  def"

          strip(
            Scaladoc,
            """  /**abc
              |   *def
              |   */
              |""".stripMargin) mustBe "abc\ndef"
        }

        "for gaps" when {

          "non-strict alignment" in {
            the[UnexpectedChar] thrownBy {
              strip(
                Scaladoc,
                """  /**abc
                  |   * def
                  | gap gap gap
                  |   * ghi
                  |   */
                  |""".stripMargin)
            } mustBe scaladoc.UnexpectedChar(2, 1, 19, 'g', Expectation.Exact('*'))
          }

          "strict alignment" in {
            the[UnexpectedChar] thrownBy {
              strip(
                Scaladoc,
                """  /**abc
                  |   * def
                  | gap gap gap
                  |   * ghi
                  |   */
                  |""".stripMargin,
                strict = true)
            } mustBe scaladoc.UnexpectedChar(2, 1, 19, 'g', Expectation.Exact(' '))

            the[UnexpectedChar] thrownBy {
              strip(
                Scaladoc,
                """  /**abc
                  |   * def
                  |   gap gap gap
                  |   * ghi
                  |   */
                  |""".stripMargin,
                strict = true)
            } mustBe scaladoc.UnexpectedChar(2, 3, 21, 'g', Expectation.Exact('*'))
          }
        }

        "for non-closed tags" in {
          val ex = the[UnexpectedEOF] thrownBy {
            strip(
              Scaladoc,
              """  /**abc
                |   * def
                |""".stripMargin) mustBe "abc\n def"
          }

          ex mustBe UnexpectedEOF(1, 8, 18, None)
        }

        "for scaladoc style #1" in {
            strip(
              Scaladoc,
              """  /**abc
                |   * def
                |   */
                |""".stripMargin,
              strict = true) mustBe "abc\n def"
        }

        "for scaladoc style #2" in {
            strip(
              Scaladoc,
              """  /** abc
                |    * def
                |    */
                |""".stripMargin,
              strict = true) mustBe "abc\ndef"
        }

        "for cases with broken indentation" in {
          // negative
          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Scaladoc,
              """  /** abc
                |   * def
                |  */
                |""".stripMargin,
              strict = true)
          }

          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Scaladoc,
              """  /** abc
                |     * def
                |    */
                |""".stripMargin,
              strict = true)
          }

          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Scaladoc,
              """  /** abc
                |    * def
                |     */
                |""".stripMargin,
              strict = true)
          }

          a[BrokenIndentation] mustBe thrownBy {
            strip(
              Scaladoc,
              """  /** abc
                |   * def
                |    */
                |""".stripMargin,
              strict = true)
          }

          // positive
          strip(
            Scaladoc,
            """  /** abc
              |   * def
              |  */
              |""".stripMargin) mustBe "abc\ndef"
        }
      }
    }
  }
}
