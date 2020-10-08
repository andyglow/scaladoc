package scaladoc.parser

import scaladoc._
import scaladoc.utils._


private[parser] trait Tokenizer {

  def chars: Array[Char]

  var pos = 0
  var lin = 0
  var col = 0

  def remaining: Int = chars.length - pos

  def next(): Unit = if (!isEOF) {
//    print(s"curr: [$char] ${char.toByte}")
    pos += 1
    if (!isEOF) {
      if (pos > 0 && chars(pos - 1).isNL) {
        lin += 1
        col = 0
      } else
        col += 1

//      println(s"; next: [$char] ${char.toByte} at ($pos) $lin:$col")

    } // else println("; EOF")
  }

  def char: Char = chars(pos)
  def setPos(x: Int): Unit = {
    pos = 0
    lin = 0
    col = 0
    (0 until x) foreach { _ => next() }
//    println(s"setPos(pos=$pos, lin=$lin, $col=$col)")
  }
  def isSlash: Boolean = nonEOF && char == '/'
  def isStar: Boolean = nonEOF && char == '*'
  def isAt: Boolean = nonEOF && char == '@'
  def isNL: Boolean = nonEOF && char.isNL
  def isEOF: Boolean = pos >= chars.length
  def nonEOF: Boolean = pos < chars.length

  /* ex: [foo]   - line starts immediately at the first position, cursor is at position 0
   * ex: [ foo]  - line prefixed with a space, cursor is at position either 0 or 1
   * ex: [  foo] - line prefixed with a 2 spaces, cursor is at position either 0 or 1 or 2
   */
  def isLineStart: Boolean = {
    val res = col == 0 || (0 until col).forall { i =>
      val p = pos - i - 1
      val c = chars(p)
      c.isWhitespace
    }

    res
  }
  def currentLine: String = {
    val start = pos - col
    var thisPos = pos - col
    while (thisPos < chars.length && !chars(thisPos).isNL) thisPos += 1
    val count = thisPos - start
    if (count == 0) EmptyString else new String(chars, start, count)
  }
  def skipWhile(pred: Char => Boolean): (Int, Int) = {
    var totalLen = 0
    var currentLineLen = 0
    while (!isEOF && pred(chars(pos))) {
      next()
      totalLen += 1
      currentLineLen += 1
      if (!isEOF && isNL) currentLineLen = 0
    }
    (totalLen, currentLineLen)
  }
  def skipSpaces(): Unit = {
    while (!isEOF && chars(pos).isSpace) {
      next()
    }
  }
  def skipUntilChar(pred: Char): Int = {
    val start = 0
    while (!isEOF && chars(pos) != pred) next()
    pos - start - 1
  }
  def skipUntil(pred: PartialFunction[(Char, Char), Int]): (Int, Int) = {
    var totalLen = 0
    var currentLineLen = 0
    var prev: Char = 0
    var skipCharsAtRight = 0
    while ({
      pos < chars.length && {
        val stop = pred.isDefinedAt(prev, chars(pos))
        if (stop) skipCharsAtRight = pred(prev, chars(pos))
        !stop
      }
    }) {
      prev = chars(pos)
      next()
      totalLen += 1
      currentLineLen += 1
      if (isNL) currentLineLen = 0
    }
    if (!isEOF) next()

    (totalLen - skipCharsAtRight + 1, currentLineLen - skipCharsAtRight + 1)
  }

  def takeUntilNL(): String = {
    val start = pos
    while (!isEOF && !isNL) next()
    val count = pos - start
    if (count == 0) EmptyString else new String(chars, start, count)
  }
  def takeWhile(pred: Char => Boolean): String = {
    var totalLen = 0
    while (!isEOF && pred(chars(pos))) {
      next()
      totalLen += 1
    }
    if (totalLen == 0) EmptyString else new String(chars, pos - totalLen, totalLen)
  }
  def scanWhile(pred: Char => Boolean): Int = {
    var thisPos = pos
    while (thisPos < chars.length && pred(chars(thisPos))) {
      thisPos += 1
    }
    thisPos - pos - 1
  }
//  def takeUntilStr(pred: String): String = {
//    val start = pos
//    val buf = Array.ofDim[Char](pred.length)
//    def hasMatching = buf.indices.forall(i => buf(i) == pred(i))
//    while (pos < chars.length && !hasMatching) {
//      (0 until (buf.length - 1)) foreach { i =>
//        buf(i) = buf(i + 1)
//      }
//      buf(buf.length) = chars(pos)
//      next()
//    }
//
//    val count = Math.max(0, pos - start - pred.length)
//    val res = if (count == 0) EmptyString else new String(chars, start, count)
//    if (!isEOF) next()
//    res
//  }
  def takeUntil2(pred: PartialFunction[(Char, Char), Int]): String = {
    val start = pos
    var prev: Char = 0
    var skipCharsAtRight = 0
    while ({
      pos < chars.length && {
        val stop = pred.isDefinedAt(prev, chars(pos))
        if (stop) skipCharsAtRight = pred(prev, chars(pos))
        !stop
      }
    }) {
      prev = chars(pos)
      next()
    }

    val count = Math.max(0, pos - start - skipCharsAtRight)
    val res = if (count == 0) EmptyString else new String(chars, start, count)
    if (!isEOF) next()
    res
  }
  def takeUntil3(pred: PartialFunction[(Char, Char, Char), Int]): String = {
    val start = pos
    var prevPrev: Char = 0
    var prev: Char = 0
    var skipCharsAtRight = 0
    while ({
      pos < chars.length && {
        val triple = (prevPrev, prev, chars(pos))
        val stop = pred.isDefinedAt(triple)
        if (stop) skipCharsAtRight = pred(triple)
        !stop
      }
    }) {
      prevPrev = prev
      prev = chars(pos)
      next()
    }

    val count = Math.max(0, pos - start - skipCharsAtRight)
    val res = if (count == 0) EmptyString else new String(chars, start, count)
    if (!isEOF) next()
    res
  }
  def throwUnexpectedEOF(exp: Option[Expectation]): Nothing = throw UnexpectedEOF(lin, col, pos, exp)
  def throwUnexpectedChar(exp: Expectation): Nothing = throw UnexpectedChar(lin, col, pos, chars(pos), exp)
  def throwUnexpected(exp: Expectation): Nothing = if (isEOF) throwUnexpectedEOF(Some(exp)) else throwUnexpectedChar(exp)
  def throwBrokenIndentation(actual: Int, expected: Int): Nothing = throw BrokenIndentation(lin, col, pos, actual, expected)
  def throwBufferFlushError(tag: Option[MutableTag], buffer: StringBuilder): Nothing = throw BufferFlushError(lin, col, pos, tag, buffer)
  def throwEmptyGroupPriority(): Nothing = throw EmptyGroupPriority(lin, col, pos)
  def throwUnexpectedParSep(offset: Int, len: Int): Nothing = throw UnexpectedParagraphSeparator(lin, col, pos + offset, len)

  def reset(): Unit = {
    pos = 0
    lin = 0
    col = 0
  }
}
