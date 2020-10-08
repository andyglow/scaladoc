package scaladoc.utils

import java.io.{StringWriter, Writer}

import scaladoc._
import Tag._
import Markup._

import scala.io.Source


object DumpAst {

  def apply(x: Scaladoc, w: IdentWriter): Unit = {
    w.writeln("scaladoc")
    w.inc()
    x.tags foreach {
      DumpAst(_, w)
    }
    w.dec()
  }

  def apply(x: Markup, w: IdentWriter): Unit = x match {
    case Document(children) =>
      w.setBorder('|')
//      w.inc()
      val nested = IdentWriter()
      children foreach { DumpAst(_, nested) }
      w.dump(nested.toString)
//      w.dec()
      w.resetBorder()
    case Paragraph(children) =>
      w.writeln("\n<p>")
      w.inc()
      val nested = IdentWriter()
      children foreach { DumpAst(_, nested) }
      w.dump(nested.toString)
      w.dec()
      w.writeln("</p>")
    case Monospace(text) => w.write(s"<pre>$text</pre>")
    case Italic(text) => w.write(s"<i>$text</i>")
    case Bold(text) => w.write(s"<b>$text</b>")
    case Underline(text) => w.write(s"<u>$text</u>")
    case Superscript(text) => w.write(s"<sup>$text</sup>")
    case Subscript(text) => w.write(s"<sub>$text</sub>")
    case Link(text) => w.write(s"<a>$text</a>")
    case CodeBlock(text) =>
      w.writeln("<code>")
      w.inc()
      w.dump(text)
      w.dec()
      w.writeln("</code>")
    case PlainText(text) => w.write(text)
    case Heading(level, text) => val hh = s"h${level.value}"; w.write(s"<$hh>$text</$hh>")
    case _ =>
  }

  def apply(x: Tag, w: IdentWriter): Unit = {
    def dumpM(m: Markup): Unit = {
      w.inc()
      DumpAst(m, w)
      w.dec()
    }

    x match {
      case Constructor(m)          =>
        w.writeln("[constructor]")
        dumpM(m)
      case Param(n, m)             =>
        w.writeln(s"[param: $n]")
        dumpM(m)
      case TypeParam(n, m)         =>
        w.writeln(s"[tparam: $n]")
        dumpM(m)
      case Returns(m)              =>
        w.writeln("[returns]")
        dumpM(m)
      case Throws(n, m)            =>
        w.writeln(s"[throws: $n]")
        dumpM(m)
      case See(m)                  =>
        w.writeln("[see]")
        dumpM(m)
      case Author(m)               =>
        w.writeln(s"[author: $m]")
      case Version(m)              =>
        w.writeln(s"[version: $m]")
      case Since(m)                =>
        w.writeln(s"[since: $m]")
      case Note(m)                 =>
        w.writeln("[note]")
        dumpM(m)
      case Example(m)              =>
        w.writeln("[example]")
        dumpM(m)
      case UseCase(m)              =>
        w.writeln("[usecase]")
        dumpM(m)
      case Todo(m)                 =>
        w.writeln("[todo]")
        dumpM(m)
      case Deprecated(m)           =>
        w.writeln("[deprecated]")
        dumpM(m)
      case Migration(m)            =>
        w.writeln("[migration]")
        dumpM(m)
      case Group(id)               =>
        w.writeln(s"[group: $id]")
      case GroupName(id, v)        =>
        w.writeln(s"[groupname: $id $v]")
      case GroupDescription(id, m) =>
        w.writeln(s"[groupdescr: $id]")
        dumpM(m)
      case GroupPriority(id, v)    =>
        w.writeln(s"[groupprio: $id $v]")
      case Documentable            =>
        w.writeln("[documentable]")
      case InheritDoc              =>
        w.writeln("[inheritdoc]")
      case OtherTag(n, m)          =>
        w.writeln(s"[$n]")
        dumpM(m)
      case Description(m)          =>
        w.writeln("[description]")
        dumpM(m)
    }
  }
}

class IdentWriter(w: Writer) {
  private var level          = 0
  private var indent: String = _
  private var border: Char = 0

  def inc(): Unit = {
    indent = null; level += 1
  }

  def dec(): Unit = if (level > 0) {
    indent = null; level -= 1
  }

  def setBorder(x: Char): Unit = border = x
  def resetBorder(): Unit = border = 0

  private def ensureIndent(): Unit =
    if (indent == null) indent = {
      if (border > 0) ("  " * level) + border
      else "  " * level
    }

  private def writeIdent(): Unit = {
    ensureIndent()
    w.write(indent)
  }

  def writeln(x: String): Unit = {
    writeIdent()
    w.write(x)
    w.write('\n')
  }

  def write(x: String): Unit = w.write(x)

  def dump(x: String): Unit = {
    x.linesIterator.filterNot(_.isVain) foreach writeln
  }

  override def toString: String = {
    w.flush()
    w.toString
  }
}

object IdentWriter {

  def apply(): IdentWriter = new IdentWriter(new StringWriter())
}
