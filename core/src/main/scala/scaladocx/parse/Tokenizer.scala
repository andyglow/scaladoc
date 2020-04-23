package scaladocx.parse

import scaladocx._


private[parse] trait Tokenizer {

  def chars: Array[Char]

  var pos = 0
  var lin = 0
  var col = 0

  def next(): Unit = {
    pos += 1
    if (!isEOF) {
      if (isNL) {
        lin += 1
        col = 0
      } else
        col += 1
    }
  }

  def isSlash: Boolean = chars(pos) == '/'
  def isStar: Boolean = chars(pos) == '*'
  def isAt: Boolean = chars(pos) == '@'
  def isNL: Boolean = chars(pos).isNL
  def isEOF: Boolean = pos >= chars.length
  def skipWhile(pred: Char => Boolean): (Int, Int) = {
    var totalLen = 0
    var currentLineLen = 0
    while (!isEOF && pred(chars(pos))) {
      next()
      totalLen += 1
      currentLineLen += 1
      if (isNL) currentLineLen = 0
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
  def takeUntil(pred: PartialFunction[(Char, Char), Int]): String = {
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
  def throwUnexpectedEOF(exp: Option[Expectation]): Nothing = throw UnexpectedEOF(lin, col, pos, exp)
  def throwUnexpectedChar(exp: Expectation): Nothing = throw UnexpectedChar(lin, col, pos, chars(pos), exp)
  def throwUnexpected(exp: Expectation): Nothing = if (isEOF) throwUnexpectedEOF(Some(exp)) else throwUnexpectedChar(exp)
  def throwBrokenIndentation(actual: Int, expected: Int): Nothing = throw BrokenIndentation(lin, col, pos, actual, expected)
  def throwBufferFlushError(tag: Option[MutableTag], buffer: StringBuilder): Nothing = throw BufferFlushError(lin, col, pos, tag, buffer)
  def throwEmptyGroupPriority(): Nothing = throw EmptyGroupPriority(lin, col, pos)

  def reset(): Unit = {
    pos = 0
    lin = 0
    col = 0
  }
}
