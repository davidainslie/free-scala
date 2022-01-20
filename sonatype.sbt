import xerial.sbt.Sonatype.GitHubHosting

credentials += Credentials(Path.userHome / ".sbt" / "sonatype-credentials")
publishTo := sonatypePublishToBundle.value
sonatypeProfileName := "davidainslie"
publishMavenStyle := true
licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))
sonatypeProjectHosting := Some(GitHubHosting("davidainslie", "free-scala", "dainslie@gmail.com"))