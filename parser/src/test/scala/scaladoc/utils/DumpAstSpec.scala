package scaladoc.utils

import scaladoc._
import scaladoc.ofmodels.Docstrings

class DumpAstSpec extends FSpec {

  private def dump(docstring: String): String = {
    val w = IdentWriter()
    DumpAst(Scaladoc.fromDocstring(docstring).fold(e => fail(e.getMessage), identity), w)
    w.toString
  }

  test("every tag") {
    val out = dump(Docstrings.KitchenSink)
    Seq(
      "[description]", "[tparam: T]", "[param: a]", "[param: b]", "[constructor]", "[returns]",
      "[throws: java.lang.IllegalStateException]", "[see]", "[note]", "[example]", "[usecase]",
      "[author: someone]", "[version: 1.0]", "[since: 0.1]", "[todo]", "[deprecated]", "[migration]",
      "[group: g1]", "[groupname: g1 Group One]", "[groupdescr: g1]", "[groupprio: g1 10]", "[documentable]",
      "[inheritdoc]", "[custom]"
    ) foreach { tag => out must include(tag) }
  }

  test("inline markup and headings") {
    val out = dump(Docstrings.NestedMarkup)
    out must include("<h1>Level 1</h1>")
    out must include("<h6>Level <pre>6</pre></h6>")
    out must include("<b>bold <i>italic</i> <a>scala.Option</a></b>, <u><i>a</i></u>, x<sup><i>2</i></sup> and y<sub><b>i</b></sub>, see <a href=\"https://scala-lang.org\">the <i>Scala</i> site</a>.")
  }
}
