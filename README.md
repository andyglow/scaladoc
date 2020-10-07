#### WORK IS STILL PRIMARILY IN PROGRESS, BUT...

# Scaladoc X

[![Build Status](https://cloud.drone.io/api/badges/andyglow/scaladocx/status.svg)](https://cloud.drone.io/andyglow/scaladocx)
[![codecov](https://codecov.io/gh/andyglow/scaladocx/branch/master/graph/badge.svg)](https://codecov.io/gh/andyglow/scaladocx)

Original `Scaladoc` [Documentation link](https://docs.scala-lang.org/overviews/scaladoc/for-library-authors.html)

This project aims to provide comprehensive toolset for scaladoc manipulations. 
- Scaladoc AST (Tags, Markup) 
- Parser
- Compiler Plugin that can be used to embed scaladocs of classes into classes bytecode so this can be reused later on
- Extractor that can be used to extract scaladocs for class from
  - attachments (see scala macro for attachments)
  - class bytecode
  - source file
 
## Scaladoc AST
This Scaladoc model allows user to have pretty detailed view on class description.
Here are the model elements. 
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
val scaladoc = Scaladoc.of[Foo]
``` 