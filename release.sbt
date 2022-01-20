import ReleaseTransformations._

// Overridden to not include "v" prefix i.e. default would have been v1.2.0 but instead we get 1.2.0
releaseTagName := s"${if (releaseUseGlobalVersion.value) (ThisBuild / version).value else version.value}"

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,       // ReleaseStep which performs the initial git checks
  tagRelease,
  // publishArtifacts,        // ReleaseStep which checks whether `publishTo` is properly set up
  setNextVersion,
  commitNextVersion,
  pushChanges                 // ReleaseStep which also checks that an upstream branch is properly configured
)