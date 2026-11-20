#### WORK IS STILL PRIMARILY IN PROGRESS, BUT...

# Scaladoc

[![CI](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml/badge.svg)](https://github.com/andyglow/scaladoc/actions/workflows/ci.yml)
[![mvn: 2.12](https://img.shields.io/badge/dynamic/json.svg?label=mvn%3A%202.12&query=%24.response.docs%5B0%5D.latestVersion&url=https%3A%2F%2Fsearch.maven.org%2Fsolrsearch%2Fselect%3Fq%3Dscaladoc-ast_2.13%26start%3D0%26rows%3D1)](https://search.maven.org/artifact/com.github.andyglow/scaladoc-ast_2.12/)
[![mvn: 2.13](https://img.shields.io/badge/dynamic/json.svg?label=mvn%3A%202.13&query=%24.response.docs%5B0%5D.latestVersion&url=https%3A%2F%2Fsearch.maven.org%2Fsolrsearch%2Fselect%3Fq%3Dscaladoc-ast_2.13%26start%3D0%26rows%3D1)](https://search.maven.org/artifact/com.github.andyglow/scaladoc-ast_2.13/)


This project aims to provide comprehensive toolset for scaladoc manipulations. 
- Scaladoc AST (Tags, Markdown) 
- Parser
- Compiler Plugin that can be used to embed scaladocs of classes into classes bytecode so this can be reused later on
- Extractor that can be used to extract scaladocs for class from
  - attachments (see scala macro for attachments)
  - class bytecode
  - source file

> Original `Scaladoc` [Documentation link](https://docs.scala-lang.org/overviews/scaladoc/for-library-authors.html)
 
## Scaladoc AST
This Scaladoc model allows user to have pretty detailed view on class description.
It covers not only scaladoc tags but also recognizes markdown markup if used for certain type of tags. 

Here is the model. 
```
Span     = PlainText (String)
         | Monospace (String)
         | Italic (String)
         | Bold (String)
         | Underline (String)
         | Superscript (String)
         | Subscript (String)
         | Link (String)
         | CodeBlock (String)

Markup   = Span
         | Heading (Level String)
         | Paragraph (List Span)
         | Document (List Markup)

Tag      = Description       (Markup)       
         | Constructor       (Markup)       
         | Param             (String Markup)
         | TypeParam         (String Markup)
         | Returns           (Markup)       
         | Throws            (String Markup)
         | See               (Markup)
         | Note              (Markup)
         | Example           (Markup)
         | UseCase           (Markup)
         | Author            (String)
         | Version           (String)
         | Since             (String)
         | Todo              (Markup)
         | Deprecated        (Markup)
         | Migration         (Markup)
         | Group             (String)
         | GroupName         (String String)
         | GroupDescription  (String Markup)
         | GroupPriority     (String Int)
         | Documentable
         | InheritDoc
         | OtherTag          (String Markup)
    
Scaladoc = List Tag
```

## Parser
Parser is just parser. Takes text and either gives you parsed model or an error.
```scala
import scaladoc._

val text = """/** = Title =
             |  *
             |  * Description
             |  *
             |  * @param a Param A
             |  * @param b Param B
             |  */
             |""".stripMargin

val scaladoc = Scaladoc fromString text
```

On Scala 3, `Symbol.docstring` returns the comment as written, except that the indentation of its first line is
gone. `Scaladoc.fromDocstring(docstring)` parses that; with `strict = true` it also validates the alignment of the
`*` border the way the Scala 2 extractor does.

```scala
import scaladoc._
// in a macro: TypeRepr.of[T].typeSymbol.docstring.map(Scaladoc.fromDocstring(_, strict = true))
val scaladoc = Scaladoc.fromDocstring("/** Description\n    *\n    * @param a Param A\n    */", strict = true)
```

The parser is plain Scala over `String`. On Scala 3 it has no dependencies besides `scaladoc-ast`.
## Compiler plugin
In scala world we got used to the idea that pretty much anything we can derive
out of whatever we have - case classes (product), sealed traits (sum), etc. 
And that is cool. But sometimes you need more, you want tour models to be documented and moreover, 
that documentation to be distributed alongside with the bytecode and can be used by other 
tools that derives something. 

Like tools that derives json schemas, avro schemas, swagger, openapi, etc. could use that info 
to enrich resulted schemas with titles, descriptions, etc based on only scaladoc.

So what this plugin does is
- validate scaladoc
- packs it into java annotation so that it could be carried over

## Extractor
This guy is used to extract scaladoc information from specified type.
It will inspect all possible places to get the job done, it will check attachments, 
java annotations and lastly source code (if available) to provide your tool with extracted scaladoc.

```scala
import scaladoc._

val scaladoc = Scaladoc.of[Foo]
```

On Scala 3 `Scaladoc.of[Foo]` reads `Symbol.docstring`. The compiler only keeps doc comments of the sources it
compiles in the current run unless the calling module is compiled with `-Yread-docs`
(`scalacOptions += "-Yread-docs"`), which also reads them from TASTy: other modules, jars and files skipped by an
incremental compile. Scala 2's source-based extraction has the same incremental-compile limitation. 

## Usage
`scaladoc-ast` and `scaladoc-parser` are published for Scala 2.12, 2.13 and 3. The compiler plugin is Scala 2 only.

For AST, Parser and Extractor please add following two lines into your `build.sbt`
```sbt
libraryDependencies += "com.github.andyglow" %% "scaladoc-ast"    % "<version>"
libraryDependencies += "com.github.andyglow" %% "scaladoc-parser" % "<version>"
```

For scaladoc embedding functionality, please consider adding these lines into your `build.sbt`
```sbt
autoCompilerPlugins := true

libraryDependencies += compilerPlugin("com.github.andyglow" % ("scaladoc-compiler-plugin_" + scalaVersion.value) % "<version>")
libraryDependencies += "com.github.andyglow" %% "scaladoc-parser" % "<version>" % Provided
libraryDependencies += "com.github.andyglow" %% "scaladoc-ast"    % "<version>"
```