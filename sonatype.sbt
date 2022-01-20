// Following instructions from https://github.com/xerial/sbt-sonatype
// see https://issues.sonatype.org/browse/OSSRH-27720
pomExtra in Global :=
  <inceptionYear>2022</inceptionYear>
    <scm>
      <url>git@github.com:davidainslie/free-scala.git</url>
      <connection>scm:git:git@github.com:davidainslie/free-scala.git</connection>
    </scm>
    <developers>
      <developer>
        <id>davidainslie</id>
        <name>David Ainslie</name>
        <url>https://github.com/davidainslie/free-scala</url>
      </developer>
    </developers>