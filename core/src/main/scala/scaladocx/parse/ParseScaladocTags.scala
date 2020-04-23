package scaladocx.parse

import scaladocx.{Markup, Scaladoc, ScaladocException, Tag}

import scala.collection.mutable

final class ParseScaladocTags(val chars: Array[Char]) extends Tokenizer {
  import MutableTag._

  private val tags = new mutable.ArrayBuffer[MutableTag]()
  private def top: Option[MutableTag] = tags.lastOption
  private val buf = new StringBuilder
  // TODO
  private def parseMarkup(): Markup = Markup.PlainText(buf.toString.trim)
  private def flush(): Unit = top match {
    case None =>
      if (buf.nonEmpty) throwBufferFlushError(None, buf)
    case Some(tag) =>
      tag match {
        case tag: Constructor if tag.isOpen => tag.markup = parseMarkup()
        case tag: Param       if tag.isOpen => tag.markup = parseMarkup()
        case tag: TypeParam   if tag.isOpen => tag.markup = parseMarkup()
        case tag: Returns     if tag.isOpen => tag.markup = parseMarkup()
        case tag: Throws      if tag.isOpen => tag.markup = parseMarkup()
        case tag: See         if tag.isOpen => tag.link = buf.toString.trim
        case tag: Note        if tag.isOpen => tag.markup = parseMarkup()
        case tag: Example     if tag.isOpen => tag.markup = parseMarkup()
        case tag: UseCase     if tag.isOpen => tag.markup = parseMarkup()
        case tag: Author      if tag.isOpen => tag.text = buf.toString.trim
        case tag: Version     if tag.isOpen => tag.text = buf.toString.trim
        case tag: Since       if tag.isOpen => tag.text = buf.toString.trim
        case tag: Todo        if tag.isOpen => tag.markup = parseMarkup()
        case tag: Deprecated  if tag.isOpen => tag.markup = parseMarkup()
        case tag: Migration   if tag.isOpen => tag.markup = parseMarkup()
        case tag: OtherTag    if tag.isOpen => tag.markup = parseMarkup()
        case tag: Description if tag.isOpen => tag.markup = parseMarkup()
        case _ =>
          if (buf.nonEmpty) throwBufferFlushError(Some(tag), buf)
      }

      buf.setLength(0)
  }

  private def takeId(): String = {
    val ident = takeWhile(_.isLetterOrDigit)
    skipSpaces()
    ident
  }

  def run(): Scaladoc = {
    while (!isEOF) {
      if (isAt) {
        flush()
        next()
        val tagName = takeWhile(_.isLetterOrDigit)
        tagName match {
          case "constructor" =>
            skipSpaces()
            tags += Constructor(null)
          case "param" =>
            skipSpaces()
            tags += Param(takeId(), null)
          case "tparam" =>
            skipSpaces()
            tags += TypeParam(takeId(), null)
          case "returns" =>
            skipSpaces()
            tags += Returns(null)
          case "throws" =>
            skipSpaces()
            tags += Throws(takeId(), null)
          case "see" =>
            skipSpaces()
            tags += See(null)
          case "note" =>
            skipSpaces()
            tags += Note(null)
          case "example" =>
            skipSpaces()
            tags += Example(null)
          case "usecase" =>
            skipSpaces()
            tags += UseCase(null)
          case "author" =>
            skipSpaces()
            tags += Author(null)
          case "version" =>
            skipSpaces()
            tags += Version(null)
          case "since" =>
            skipSpaces()
            tags += Since(null)
          case "todo" =>
            skipSpaces()
            tags += Todo(null)
          case "deprecated" =>
            skipSpaces()
            tags += Deprecated(null)
          case "migration" =>
            skipSpaces()
            tags += Migration(null)
          case "group" =>
            skipSpaces()
            tags += Group(takeId())
          case "groupname" =>
            skipSpaces()
            tags += GroupName(takeId(), takeId())
          case "groupdesc" =>
            skipSpaces()
            tags += GroupDescription(takeId(), null)
          case "groupprio" =>
            skipSpaces()
            val name = takeId()
            tags += GroupPriority(name, {
              skipSpaces()
              val digs = takeWhile(_.isDigit)
              if (digs.isEmpty) throwEmptyGroupPriority()
              digs.toInt
            })
          case "documentable" =>
            skipSpaces()
            tags += Documentable
          case "inheritdoc" =>
            skipSpaces()
            tags += InheritDoc
          case _ =>
            skipSpaces()
            tags += OtherTag(tagName, null)
        }
      } else {
        top match {
          case None                       => tags += Description(null)
          case Some(tag) if !(tag.isOpen) => tags += Description(null)
          case _ =>
        }
        buf append chars(pos)
        next()
      }
    }
    flush()

    Scaladoc(tags.toList map { _.toTag })
  }
}

object ParseScaladocTags {

  def pure(x: String): Either[ScaladocException, Scaladoc] = {
    try Right(ParseScaladocTags(x.toCharArray)) catch {
      case ex: ScaladocException => Left(ex)
    }
  }

  def apply(x: Array[Char]): Scaladoc = {
    new ParseScaladocTags(x).run()
  }

  def main(args: Array[String]): Unit = {
    val text =
      """foo bar
        |
        |@param a Param A
        |@param b Param B
        |@tparam A Type A
        |@tparam B Type B
        |@group x
        |@groupprio x 86
        |""".stripMargin

    println(ParseScaladocTags(text.toCharArray))
  }
}