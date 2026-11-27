package scaladoc.parser

import org.scalatest.funsuite.AnyFunSuite

/** Inputs that make a careless parser quadratic or worse, after cmark's `pathological_tests.py`. Each is big enough
  * for that to take hundreds of times as long as ordinary text of its size, and has to parse in at most 50 times as
  * long (linear cases with big constants, like nested quotes parsed once a level, run at about 20): measured against
  * that, rather than the clock, it holds on a slow machine and with coverage instrumentation.
  */
trait PathologicalInput { this: AnyFunSuite =>

  protected val N = 30000

  protected def r(s: String, n: Int = N): String = s * n

  /** Ordinary text of `length` characters in the syntax under test. */
  protected def ordinary(length: Int): String

  // the fastest of a few runs, after a warm-up: a single run is off by a GC pause or a JIT compile, and the baseline's
  // error is multiplied by the factor
  private def nanos(f: => Any): Long = {
    f
    (1 to 3).map { _ =>
      val start = System.nanoTime()
      f
      System.nanoTime() - start
    }.min
  }

  private def ms(nanos: Long): String = f"${nanos / 1e6}%.1f ms"

  protected def quick(input: String)(parse: String => Any): Unit = {
    val text     = ordinary(input.length)
    val baseline = nanos(parse(text))
    val took     = nanos(parse(input))
    assert(took <= math.max(50000000L, 50 * baseline), s"took ${ms(took)}, ordinary text of its size ${ms(baseline)}")
  }
}
