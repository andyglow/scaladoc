package scaladoc.parser

import org.scalactic.source.Position
import scaladoc._
import scaladoc.Markup._
import scaladoc.Markup.Heading._
import scaladoc.matchers.Matchers._


class ParseMarkupSpec extends WSpec {

  "ParseMarkup" must {

    "handle empty docs" in {
      ParseMarkup("") mustBe Document(Nil)
      ParseMarkup("   ") mustBe Document(Nil)
      ParseMarkup("\n  \n") mustBe Document(Nil)
    }

    "put text into paragraphs, also a single one and the one next to a block" in {
      ParseMarkup("one\nparagraph") mustBe Document(Paragraph(PlainText("one\nparagraph")))
      ParseMarkup("Before\n{{{x}}}\nAfter") mustBe Document(
        Paragraph(PlainText("Before")), CodeBlock("x"), Paragraph(PlainText("After")))
    }

    "handle basic markup" when {
      def testMatching(str: String, expectation: Document)(implicit pos: Position): Unit = {
        withClue(s"[$str]") { ParseMarkup(str) mustBe expectation }
      }

      def testSuite(str: String, expectation: Span): Unit = {
        testMatching(str, Document(Paragraph(expectation)))
        testMatching(s" $str ", Document(Paragraph(expectation)))
        testMatching(s"a $str b", Document(Paragraph(PlainText("a "), expectation, PlainText(" b"))))
      }

      def headingTestSuite(str: String, expectation: Heading): Unit = {
        testMatching(str, Document(expectation))
        testMatching(s" $str ", Document(expectation))
        testMatching(s"  $str ", Document(expectation))
        testMatching(s"\t $str ", Document(expectation))
        testMatching(s" $str\t", Document(expectation))
      }

      "monospace" in testSuite("`abc`", Monospace("abc"))
      "italic" in testSuite("''abc''", Italic("abc"))
      "bold" in testSuite("'''abc'''", Bold("abc"))
      "underline" in testSuite("__abc__", Underline("abc"))
      "superscript" in testSuite("^abc^", Superscript("abc"))
      "subscript" in testSuite(",,abc,,", Subscript("abc"))
      "link" in testSuite("[[abc]]", Link("abc"))

      "h1" in headingTestSuite("=abc=", Heading(One, "abc"))
      "h2" in headingTestSuite("==abc==", Heading(Two, "abc"))
      "h3" in headingTestSuite("===abc===", Heading(Three, "abc"))
      "h4" in headingTestSuite("====abc====", Heading(Four, "abc"))
      "h5" in headingTestSuite("=====abc=====", Heading(Five, "abc"))
      "h6" in headingTestSuite("======abc======", Heading(Six, "abc"))
      "h7 and deeper are h6" in headingTestSuite("=======abc=======", Heading(Six, "abc"))
    }

    "handle paragraphs" in {
      val doc = ParseMarkup(
        """Start the comment here
          |and use the left star followed by a
          |white space on every line.
          |
          |Even on empty paragraph-break lines.
          |
          |Note that the * on each line is aligned
          |with the second * in /** so that the
          |left margin is on the same column on the
          |first line and on subsequent ones.
          |
          |The closing Scaladoc tag goes on its own,
          |separate line. E.g.
          |
          |Calculate the square of the given number""".stripMargin)

      doc mustBe Document(
        Paragraph(PlainText(
          """Start the comment here
            |and use the left star followed by a
            |white space on every line.""".stripMargin)),
        Paragraph(PlainText("Even on empty paragraph-break lines.")),
        Paragraph(PlainText(
          """Note that the * on each line is aligned
            |with the second * in /** so that the
            |left margin is on the same column on the
            |first line and on subsequent ones.""".stripMargin)),
        Paragraph(PlainText(
          """The closing Scaladoc tag goes on its own,
            |separate line. E.g.""".stripMargin)),
        Paragraph(PlainText("Calculate the square of the given number")))
    }

    "handle little more complex example" in {
      val doc = ParseMarkup(
        """= A Title =
          |
          |A class to represent a ''human being''.
          |
          |== A Subtitle ==
          |Specify the `name`, `age`, and `weight` when creating a new `Person`,
          |then access the fields like this:
          |
          |{{{
          | val p = Person("Al", 42, 200.0)
          | p.name
          | p.age
          | p.weight
          |}}}
          |
          |Did you know: The [[com.acme.foo.Employee]] extends this class.""".stripMargin)

      doc must (coincideWith (Document(
        Heading(One, "A Title"),
        Paragraph(
          PlainText("A class to represent a "),
          Italic("human being"),
          PlainText(".")),
        Heading(Two, "A Subtitle"),
        Paragraph(
          PlainText("Specify the "),
          Monospace("name"),
          PlainText(", "),
          Monospace("age"),
          PlainText(", and "),
          Monospace("weight"),
          PlainText(" when creating a new "),
          Monospace("Person"),
          PlainText(",\nthen access the fields like this:")),
        CodeBlock(
          """val p = Person("Al", 42, 200.0)
            |p.name
            |p.age
            |p.weight""".stripMargin),
        Paragraph(
          PlainText("Did you know: The "),
          Link("com.acme.foo.Employee"),
          PlainText(" extends this class.")))))
    }

    "keep an unclosed marker as text" in {
      def text(x: String) = Document(Paragraph(PlainText(x)))
      ParseMarkup("x^2 is square") mustBe text("x^2 is square")
      ParseMarkup("a `b c") mustBe text("a `b c")
      ParseMarkup("it''s fine") mustBe text("it''s fine")
      ParseMarkup("'''a'' b") mustBe text("'''a'' b")
      ParseMarkup("[[open link") mustBe text("[[open link")
      ParseMarkup("{{{ never closed") mustBe text("{{{ never closed")
      ParseMarkup("x^2 and ^sup^") mustBe Document(Paragraph(PlainText("x"), Superscript("2 and "), PlainText("sup^")))
    }

    "not close a marker in the next paragraph" in {
      ParseMarkup("a `b\n\nc` d") mustBe Document(Paragraph(PlainText("a `b")), Paragraph(PlainText("c` d")))
      ParseMarkup("a ''b\nc'' d") mustBe Document(Paragraph(PlainText("a "), Italic("b\nc"), PlainText(" d")))
    }

    "let a code block span paragraphs" in {
      ParseMarkup("{{{\na\n\nb\n}}}") mustBe Document(CodeBlock("a\n\nb"))
    }

    "drop the indentation that all lines of a code block share" in {
      ParseMarkup("{{{\n\n    def f =\n      1\n\n    f   \n  }}}") mustBe Document(CodeBlock("def f =\n  1\n\nf"))
      ParseMarkup("{{{ x }}}") mustBe Document(CodeBlock("x"))
    }

    "nest inline markup" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("'''bold ''italic'' bold'''") mustBe p(Bold(PlainText("bold "), Italic("italic"), PlainText(" bold")))
      ParseMarkup("'''a ''b'''''") mustBe p(Bold(PlainText("a "), Italic("b")))
      ParseMarkup("''a '''b''' c''") mustBe p(Italic(PlainText("a "), Bold("b"), PlainText(" c")))
      ParseMarkup("__see [[Foo]]__") mustBe p(Underline(PlainText("see "), Link("Foo")))
      ParseMarkup("x^''i''^ y,,`n`,,") mustBe p(PlainText("x"), Superscript(Italic("i")), PlainText(" y"), Subscript(Monospace("n")))
      ParseMarkup("'''a ''b c'''") mustBe p(Bold("a ''b c"))
      ParseMarkup("`a ''b'' c`") mustBe p(Monospace("a ''b'' c"))
    }

    "parse the markup of a heading" in {
      ParseMarkup("= The `App` trait =") mustBe Document(Heading(One, List(PlainText("The "), Monospace("App"), PlainText(" trait"))))
      ParseMarkup("== ''a'' ==") mustBe Document(Heading(Two, List(Italic("a"))))
    }

    "parse a char array like a string" in {
      ParseMarkup("a ''b''".toCharArray, Log.Noop) mustBe ParseMarkup("a ''b''")
    }

    "drop whitespace between blocks and empty code" in {
      ParseMarkup("{{{a}}}   {{{b}}}") mustBe Document(CodeBlock("a"), CodeBlock("b"))
      ParseMarkup("{{{ \n }}}") mustBe Document(CodeBlock(""))
      ParseMarkup("`` ^ ^") mustBe Document(Nil)
    }

    "parse links with a title" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("[[scala.Option]]") mustBe p(Link("scala.Option"))
      ParseMarkup("[[https://scala-lang.org Scala Site]]") mustBe p(Link("https://scala-lang.org", "Scala Site"))
      ParseMarkup("[[x  the '''x''' ]]") mustBe p(Link("x", List(PlainText("the "), Bold("x"))))
      ParseMarkup("[[x\non the next line]]") mustBe p(Link("x", "on the next line"))
      ParseMarkup("see [[[baz(x:List[List[A]])*]]] now") mustBe p(PlainText("see "), Link("baz(x:List[List[A]])*"), PlainText(" now"))
      ParseMarkup("[[[f(a:List[A]]) f]]]") mustBe p(Link("f(a:List[A]])", "f"))
      ParseMarkup("[[ x]]") mustBe p(PlainText("[[ x]]"))
      ParseMarkup("[[x title") mustBe p(PlainText("[[x title"))
      ParseMarkup("[[[x]]") mustBe p(PlainText("[[[x]]"))
    }

    "tell a URL from an entity" in {
      Link("https://scala-lang.org").isUrl mustBe true
      Link("mailto:a@b.c").isUrl mustBe true
      Link("scala.Option").isUrl mustBe false
      Link("scala.collection.immutable.List$.apply").isUrl mustBe false
    }

    "parse lists" in {
      def item(text: String, nested: scaladoc.Markup*) = Document(Paragraph(PlainText(text)) :: nested.toList)

      // the example of the scaladoc guide
      val doc = Scaladoc.fromString(
        """/** Here is an unordered list:
          |  *
          |  *   - First item
          |  *   - Second item
          |  *     - Sub-item to the second
          |  *     - Another sub-item
          |  *   - Third item
          |  *
          |  * Here is an ordered list:
          |  *
          |  *   1. First numbered item
          |  *   1. Second numbered item
          |  *     i. Sub-item to the second
          |  *     i. Another sub-item
          |  *   1. Third item
          |  */""".stripMargin).fold(e => fail(e.getMessage), identity)

      doc.descriptions mustBe List(Document(
        Paragraph(PlainText("Here is an unordered list:")),
        UnorderedList(List(
          item("First item"),
          item("Second item", UnorderedList(List(item("Sub-item to the second"), item("Another sub-item")))),
          item("Third item"))),
        Paragraph(PlainText("Here is an ordered list:")),
        OrderedList(List(
          item("First numbered item"),
          item("Second numbered item", OrderedList(List(item("Sub-item to the second"), item("Another sub-item")), OrderedList.LowerRoman)),
          item("Third item")), OrderedList.Decimal)))
    }

    "parse list items with markup, over several lines and blank lines" in {
      ParseMarkup("Items:\n - '''a''' item\n   continued\n\n - [[x]]\nafter") mustBe Document(
        Paragraph(PlainText("Items:")),
        UnorderedList(List(
          Document(Paragraph(Bold("a"), PlainText(" item\n   continued"))),
          // as in scaladoc, a line that isn't an item continues the item before it
          Document(Paragraph(Link("x"), PlainText("\nafter"))))))
    }

    "end a list at another numbering" in {
      ParseMarkup(" - a\n 1. b\n A. c") mustBe Document(
        UnorderedList(List(Document(Paragraph(PlainText("a"))))),
        OrderedList(List(Document(Paragraph(PlainText("b")))), OrderedList.Decimal),
        OrderedList(List(Document(Paragraph(PlainText("c")))), OrderedList.UpperAlpha))
    }

    "not take a line as a list item without indentation or a space after the marker" in {
      ParseMarkup("- a\n-b\n 1.c") mustBe Document(Paragraph(PlainText("- a\n-b\n 1.c")))
    }

    "parse a nested list without a parent item, and lists in a tag" in {
      ParseMarkup("   - deep\n - top") mustBe Document(
        UnorderedList(List(Document(Paragraph(PlainText("deep"))))),
        UnorderedList(List(Document(Paragraph(PlainText("top"))))))
      ParseMarkup(" - a\n   - deep\n - b") mustBe Document(UnorderedList(List(
        Document(Paragraph(PlainText("a")), UnorderedList(List(Document(Paragraph(PlainText("deep")))))),
        Document(Paragraph(PlainText("b"))))))
      Scaladoc.fromString("/** @param x one of\n  *   - a\n  *   - b\n  */").map(_.params("x")) mustBe Right(Document(
        Paragraph(PlainText("one of")),
        UnorderedList(List(Document(Paragraph(PlainText("a"))), Document(Paragraph(PlainText("b")))))))
    }

    "parse tables" in {
      import Table.Alignment._
      def row(cells: String*) = Table.Row(cells.toList.map(c => Paragraph(if (c.isEmpty) Nil else List(PlainText(c)))))

      ParseMarkup(
        """Sizes:
          || Size | Price    | Notes |
          ||:-----|:--------:|------:|
          || S    | `1 \| 2` | ''a'' |
          || M    | 3        |
          || L    | 4        | b | extra |
          |After""".stripMargin) mustBe Document(
        Paragraph(PlainText("Sizes:")),
        Table(
          row("Size", "Price", "Notes"),
          List(Left, Center, Right),
          List(
            Table.Row(List(Paragraph(PlainText("S")), Paragraph(Monospace("1 | 2")), Paragraph(Italic("a")))),
            row("M", "3", ""),
            row("L", "4", "b"))),
        Paragraph(PlainText("After")))
    }

    "not take rows without a delimiter row as a table" in {
      ParseMarkup("| a | b |\n| c | d |") mustBe Document(Paragraph(PlainText("| a | b |\n| c | d |")))
      ParseMarkup("| a | b |\n|---|") mustBe Document(Paragraph(PlainText("| a | b |\n|---|")))
      ParseMarkup("| a |") mustBe Document(Paragraph(PlainText("| a |")))
      ParseMarkup("| a |\n") mustBe Document(Paragraph(PlainText("| a |")))
    }

    "parse a table of a header only, and an indented one" in {
      ParseMarkup("  | a |\n  |---|") mustBe Document(
        Table(Table.Row(List(Paragraph(PlainText("a")))), List(Table.Alignment.Left), Nil))
    }

    "parse a horizontal rule at the start of a block" in {
      ParseMarkup("Above\n\n----\n\nBelow") mustBe Document(Paragraph(PlainText("Above")), HorizontalRule, Paragraph(PlainText("Below")))
      ParseMarkup("  -------\nBelow") mustBe Document(HorizontalRule, Paragraph(PlainText("Below")))
      ParseMarkup("Above\n----") mustBe Document(Paragraph(PlainText("Above\n----")))
      ParseMarkup("---") mustBe Document(Paragraph(PlainText("---")))
      ParseMarkup("---- more") mustBe Document(Paragraph(PlainText("---- more")))
    }

    "parse inline HTML into the model" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("<b>a</b> <strong>b</strong> <i>c</i> <em>d</em> <u>e</u> x<sup>2</sup> y<sub>i</sub>") mustBe p(
        Bold("a"), PlainText(" "), Bold("b"), PlainText(" "), Italic("c"), PlainText(" "), Italic("d"), PlainText(" "),
        Underline("e"), PlainText(" x"), Superscript("2"), PlainText(" y"), Subscript("i"))
      ParseMarkup("<B>a <i>b</i></B>") mustBe p(Bold(PlainText("a "), Italic("b")))
      ParseMarkup("<code>List&lt;A&gt;</code> and <tt>x</tt>") mustBe p(Monospace("List<A>"), PlainText(" and "), Monospace("x"))
      ParseMarkup("""see <a href="https://x.y?a=1&amp;b=2">the <b>site</b></a>""") mustBe p(
        PlainText("see "), Link("https://x.y?a=1&b=2", List(PlainText("the "), Bold("site"))))
      ParseMarkup("a<br>b<br/>c") mustBe p(PlainText("a\nb\nc"))
    }

    "keep other safe HTML tags, and anything else as text" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("""<span class="x">a</span>""") mustBe p(HtmlTag("""<span class="x">"""), PlainText("a"), HtmlTag("</span>"))
      ParseMarkup("<b>unclosed") mustBe p(HtmlTag("<b>"), PlainText("unclosed"))
      ParseMarkup("<a name=\"x\">anchor</a>") mustBe p(HtmlTag("<a name=\"x\">"), PlainText("anchor"), HtmlTag("</a>"))
      ParseMarkup("<code>unclosed") mustBe p(HtmlTag("<code>"), PlainText("unclosed"))
      ParseMarkup("List<String> and a < b and </nope>") mustBe p(PlainText("List<String> and a < b and </nope>"))
    }

    "decode entities" in {
      ParseMarkup("a &lt; b &amp;&amp; c &gt; d &quot;&apos;&nbsp;&#65;&#x42; &unknown; & x") mustBe Document(Paragraph(
        PlainText("a < b && c > d \"' AB &unknown; & x")))
      ParseMarkup.decodeEntities("&#xZZ; &#99999999999;") mustBe "&#xZZ; &#99999999999;"
    }

    "turn HTML blocks into their markup" in {
      ParseMarkup("One<p>Two</p><div>Three</div>") mustBe Document(
        Paragraph(PlainText("One")), Paragraph(PlainText("Two")), Paragraph(PlainText("Three")))
      ParseMarkup("<h1>Title</h1>Text<h3 id=\"x\">Sub</h3>") mustBe Document(
        Heading(One, "Title"), Paragraph(PlainText("Text")), Heading(Three, "Sub"))
      ParseMarkup("Items:<ul><li>a</li><li>b</li></ul>") mustBe Document(
        Paragraph(PlainText("Items:")),
        UnorderedList(List(Document(Paragraph(PlainText("a"))), Document(Paragraph(PlainText("b"))))))
      ParseMarkup("Code:<pre><code>if (a &lt; b) x</code></pre>") mustBe Document(
        Paragraph(PlainText("Code:")), CodeBlock("if (a < b) x"))
      ParseMarkup("a<!-- hidden -->b") mustBe Document(Paragraph(PlainText("ab")))
      ParseMarkup("{{{<p>&lt;</p>}}}") mustBe Document(CodeBlock("<p>&lt;</p>"))
    }

    "turn HTML blocks into their markup whatever the case, with attributes, from left to right" in {
      ParseMarkup("<P class=\"x\">One<DIV/>Two</Div >") mustBe Document(Paragraph(PlainText("One")), Paragraph(PlainText("Two")))
      ParseMarkup("<H2\nid=x>Title</H2>") mustBe Document(Heading(Two, "Title"))
      ParseMarkup("<OL type=\"a\">\n<LI value=\"2\">a</LI></OL>") mustBe Document(
        UnorderedList(List(Document(Paragraph(PlainText("a"))))))
      ParseMarkup("<PRE class=\"x\"><CODE lang=\"scala\">a &amp;&amp; b</Code></Pre>") mustBe Document(CodeBlock("a && b"))
      // not block tags: `<h7>`, `<h1/>`, `</li >`, `<pre/>`, and a `<p` without its `>`
      ParseMarkup("<h7>a<h1/>b</li >c<pre/>d<p x") mustBe Document(
        Paragraph(PlainText("<h7>a<h1/>b</li >c"), HtmlTag("<pre/>"), PlainText("d<p x")))
      // a comment hides the tags in it, a <pre> too, and so does code
      ParseMarkup("a<!-- <pre>x</pre> <p> -->b") mustBe Document(Paragraph(PlainText("ab")))
      ParseMarkup("<pre><p>x</p><!-- y --></pre>") mustBe Document(CodeBlock("<p>x</p><!-- y -->"))
      // without its end, a comment or a <pre> is text
      ParseMarkup("a <!-- b") mustBe Document(Paragraph(PlainText("a <!-- b")))
      ParseMarkup("a <pre>b") mustBe Document(Paragraph(PlainText("a "), HtmlTag("<pre>"), PlainText("b")))
    }

    "find inline tags, their href and entities as HTML has them" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("<a class=x HREF = 'y'>t</a>") mustBe p(Link("y", List(PlainText("t"))))
      // the first href in quotes counts, and a quoted value is on one line
      ParseMarkup("<a href=x href=\"y\">t</a>") mustBe p(Link("y", List(PlainText("t"))))
      ParseMarkup("<a href=\"x\ny\">t</a>") mustBe p(HtmlTag("<a href=\"x\ny\">"), PlainText("t"), HtmlTag("</a>"))
      ParseMarkup("a<br />b <b x<i>c</i>") mustBe p(PlainText("a\nb <b x"), Italic("c"))
      ParseMarkup("&#X41;&#x;&#1a;&a1;&1a;&") mustBe p(PlainText("A&#x;&#1a;&a1;&1a;&"))
    }

    "tell a table's delimiter cells from other cells" in {
      ParseMarkup("| a | b | c | d |\n| :- | -: |\t:-:\t| - |") mustBe Document(
        Table(
          Table.Row(List(Paragraph(PlainText("a")), Paragraph(PlainText("b")), Paragraph(PlainText("c")), Paragraph(PlainText("d")))),
          List(Table.Alignment.Left, Table.Alignment.Right, Table.Alignment.Center, Table.Alignment.Left),
          Nil))
      ParseMarkup("| a |\n| : - |") mustBe Document(Paragraph(PlainText("| a |\n| : - |")))
      ParseMarkup("| a |\n| : |") mustBe Document(Paragraph(PlainText("| a |\n| : |")))
    }

    "parse javadoc's inline tags" in {
      def p(spans: Span*) = Document(Paragraph(spans.toList))

      ParseMarkup("{@code Map<K, V>} and {@code {a}}") mustBe p(Monospace("Map<K, V>"), PlainText(" and "), Monospace("{a}"))
      ParseMarkup("see {@link Foo} or {@linkplain Foo#bar the ''bar''}") mustBe p(
        PlainText("see "), Link("Foo"), PlainText(" or "), Link("Foo#bar", List(PlainText("the "), Italic("bar"))))
      ParseMarkup("a {@literal <} b, {@value MAX}{@docRoot}") mustBe p(PlainText("a < b, "), Monospace("MAX"))
      ParseMarkup("{@inheritDoc} and {@code open") mustBe p(PlainText("{@inheritDoc} and {@code open"))
      Scaladoc.fromString("/** Uses {@link java.util.List}.\n  * @param a {@code a}\n  */").map(_.tags) mustBe Right(List(
        Tag.Description(Document(Paragraph(PlainText("Uses "), Link("java.util.List"), PlainText(".")))),
        Tag.Param("a", Document(Paragraph(Monospace("a"))))))
    }

    "end a paragraph at a heading line" in {
      ParseMarkup("text\n= Title =\nmore") mustBe Document(
        Paragraph(PlainText("text")), Heading(One, "Title"), Paragraph(PlainText("more")))
    }
  }
}
