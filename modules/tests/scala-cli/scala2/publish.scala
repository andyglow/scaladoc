// Scala 2, a module that publishes its scaladoc: the compiler plugin, and the annotation it embeds scaladoc in, which
// the module's consumers get along. The option is optional: what to leave out, see "Choosing what to embed"
//> using plugin io.github.andyglow::scaladoc-compiler-plugin:@VERSION@
//> using dep io.github.andyglow:scaladoc-annotation:@VERSION@
//> using option -P:scaladoc-compiler-plugin:exclude:class:demo.model.internal.*
