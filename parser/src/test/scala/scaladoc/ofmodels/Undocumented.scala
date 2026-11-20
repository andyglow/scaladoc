package scaladoc.ofmodels

// Kept alone in its own file: Scala 2's source-based `Scaladoc.of` would otherwise pick up the closest
// preceding doc comment in the file.
case class Undocumented(a: String)
