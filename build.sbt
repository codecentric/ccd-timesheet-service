// Basic project configuration
lazy val root = (project in file(".")).
  settings(
    name := "ccd-timesheet-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

// Project dependencies
libraryDependencies ++= Seq(
  //  groupID %% artifactID % revision
)