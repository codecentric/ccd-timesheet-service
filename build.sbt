// Basic project configuration
lazy val root = (project in file(".")).
  settings(
    name := "ccd-timesheet-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

// Additional resolvers
resolvers += "spray repo" at "http://repo.spray.io"

// Project dependencies
libraryDependencies ++= Seq(
  "io.spray" %% "spray-can" % "1.3.3"
  //  groupID %% artifactID % revision
)