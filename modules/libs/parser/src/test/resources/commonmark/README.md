# CommonMark spec examples

`gfm-spec.txt` is the [GitHub Flavored Markdown spec](https://github.github.com/gfm/), version 0.29-gfm
(2019-04-06), as in [github/cmark-gfm](https://github.com/github/cmark-gfm/blob/master/test/spec.txt): the CommonMark
spec, by John MacFarlane, with GitHub's extensions (tables, task list items, strikethrough, autolinks). It is
licensed under [CC BY-SA 4.0](http://creativecommons.org/licenses/by-sa/4.0/), and is included here unmodified.

`CommonMarkSpecSuite` runs its examples through the Markdown parser. `passing.txt` lists the numbers of the examples
that pass; the suite fails when the actual set differs from it.

`MarkdownBlocks`, the parser itself, is measured: without the HTML cleaning Scala 3's scaladoc does before it parses a
comment, which `ParseMarkup` does too and `MarkdownHtmlCleaningSpec` tests.

These fail on purpose, because the parser reads Markdown as Scala 3's scaladoc does: flexmark 0.62.2 with Scala 3's
options (`scaladoc/.../tasty/comments/MarkdownParser.scala` in scala/scala3), which follows an earlier CommonMark in
places, with its extensions on:

| Examples                  | Why                                                                                       |
|---------------------------|-------------------------------------------------------------------------------------------|
| 567, 598                  | `[[...]]` is a wiki link (`[[target text]]`), as in Scala 3                               |
| 614, 616, 619, 620        | bare URLs and email addresses are links (GFM's autolinks, on in Scala 3); the spec runs these without them |
| 652                       | GFM's tagfilter is about rendering HTML, not parsing it                                   |
| 341, 342, 344–347, 660, 108 | a code span loses the whitespace around it, and a run of it inside is one space, as in flexmark |
| 116                       | a code fence's info string has no backticks, a tilde fence's either, as in flexmark          |
| 200                       | a `\|` in a table cell's code span stays `\|`, as in flexmark                               |
| 202, 203                  | a table's rows have a `\|`, and its delimiter row needn't be as wide as its header, as in flexmark |
| 66, 68                    | `---` lines around the start are YAML front matter, which flexmark drops                     |
| 439                       | `***foo* bar**`: whether a run of 3 opens emphasis around strong emphasis or the other way round depends on which closer comes first; the parser, a recursive descent one, tries emphasis first |
