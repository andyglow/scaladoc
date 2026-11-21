package scaladoc.compile

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers._

class EmbedFilterSpec extends AnyFunSuite {

  private def filter(options: String*): EmbedFilter = EmbedFilter.parse(options.toList).fold(e => fail(e), identity)

  private val modelFile    = "/work/app/src/main/scala/com/acme/model/User.scala"
  private val internalFile = "/work/app/src/main/scala/com/acme/internal/Secret.scala"

  test("no options: everything is embedded") {
    EmbedFilter.All.acceptsFile(modelFile) mustBe true
    EmbedFilter.All.accepts(modelFile, "com.acme.model.User") mustBe true
    filter().accepts(internalFile, "com.acme.internal.Secret") mustBe true
  }

  test("include:path keeps only matching files") {
    val f = filter("include:path:**/model/**")
    f.acceptsFile(modelFile) mustBe true
    f.acceptsFile(internalFile) mustBe false
    f.accepts(modelFile, "com.acme.model.User") mustBe true
    f.accepts(internalFile, "com.acme.internal.Secret") mustBe false
  }

  test("exclude:path drops matching files") {
    val f = filter("exclude:path:**/internal/**")
    f.acceptsFile(internalFile) mustBe false
    f.accepts(internalFile, "com.acme.internal.Secret") mustBe false
    f.accepts(modelFile, "com.acme.model.User") mustBe true
  }

  test("include:class keeps only matching classes, and no file can be skipped by path") {
    val f = filter("include:class:com.acme.model.*")
    f.acceptsFile(internalFile) mustBe true
    f.accepts(modelFile, "com.acme.model.User") mustBe true
    f.accepts(modelFile, "com.acme.model.sub.User") mustBe false
    f.accepts(internalFile, "com.acme.internal.Secret") mustBe false
  }

  test("include options are a union of paths and classes") {
    val f = filter("include:path:**/model/**", "include:class:com.acme.internal.Public*")
    f.accepts(modelFile, "com.acme.model.User") mustBe true
    f.accepts(internalFile, "com.acme.internal.PublicApi") mustBe true
    f.accepts(internalFile, "com.acme.internal.Secret") mustBe false
  }

  test("exclude wins over include") {
    val f = filter("include:path:**/model/**", "exclude:class:**.User")
    f.accepts(modelFile, "com.acme.model.User") mustBe false
    f.accepts(modelFile, "com.acme.model.Account") mustBe true

    val g = filter("include:class:com.acme.**", "exclude:path:**/internal/**")
    g.accepts(internalFile, "com.acme.internal.Secret") mustBe false
    g.acceptsFile(internalFile) mustBe false
  }

  test("class patterns: *, **, **. and ?") {
    def matches(pattern: String, name: String) = EmbedFilter.classPatternRegex(pattern).matcher(name).matches()

    matches("com.acme.*", "com.acme.User") mustBe true
    matches("com.acme.*", "com.acme.model.User") mustBe false
    matches("com.acme.**", "com.acme.model.User") mustBe true
    matches("com.acme.**", "com.acme") mustBe false
    matches("com.acme.**.internal.*", "com.acme.internal.Secret") mustBe true
    matches("com.acme.**.internal.*", "com.acme.a.b.internal.Secret") mustBe true
    matches("com.acme.**.internal.*", "com.acme.internals.Secret") mustBe false
    matches("**.User", "User") mustBe true
    matches("**.User", "com.acme.User") mustBe true
    matches("com.acme.Use?", "com.acme.User") mustBe true
    matches("com.acme.Outer.*", "com.acme.Outer.Inner") mustBe true
    matches("com.acme.User$", "com.acme.User$") mustBe true // regex metacharacters are literal
    matches("com.acme.User$", "com.acme.User") mustBe false
  }

  test("invalid options are rejected") {
    EmbedFilter.parse(List("include:**/model/**")).isLeft mustBe true
    EmbedFilter.parse(List("exclude:file:**/model/**")).isLeft mustBe true
    EmbedFilter.parse(List("include:class:")).isLeft mustBe true
    EmbedFilter.parse(List("skip:path:x")).isLeft mustBe true
    EmbedFilter.parse(List("include:path:{model")).isLeft mustBe true
    EmbedFilter.parse(List("include:path:**/model/**", "bogus")).left.toOption.get must include("bogus")
  }
}
