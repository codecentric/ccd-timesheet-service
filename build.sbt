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
libraryDependencies ++= {
  val akkaV = "2.3.9"
  val sprayV = "1.3.3"

  Seq(
    //  groupID %% artifactID % revision
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-routing" % sprayV,
    "io.spray" %% "spray-testkit" % sprayV % "test",
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "org.scala-lang.modules" %% "scala-xml" % "1.0.4"
  )
}