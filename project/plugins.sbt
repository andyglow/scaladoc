addSbtPlugin("com.github.sbt" % "sbt-release"        % "1.6.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp"            % "2.4.0")
addSbtPlugin("com.github.sbt" % "sbt-github-actions" % "0.32.2")
addSbtPlugin("org.scoverage"  % "sbt-scoverage"      % "2.4.4")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.23.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.4.0")

// the documentation site (the docs project): mdoc checks the snippets, Paradox renders them with the Material theme,
// sbt-site and sbt-ghpages publish the site to the gh-pages branch
addSbtPlugin("org.scalameta"         % "sbt-mdoc"                   % "2.9.2")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox"                % "0.11.1")
addSbtPlugin("com.github.sbt"        % "sbt-paradox-material-theme" % "0.8.0")
addSbtPlugin("com.github.sbt"        % "sbt-site-paradox"           % "1.8.0")
addSbtPlugin("com.github.sbt"        % "sbt-ghpages"                % "0.10.0")
