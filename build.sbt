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
    // Rest server
    "io.spray" %% "spray-can" % sprayV,
    "io.spray" %% "spray-routing" % sprayV,
    "io.spray" %% "spray-testkit" % sprayV % "test",
    // Actors
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    // Prevent warning
    "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
    // Logging
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    // Data-access
    "com.typesafe.slick" %% "slick" % "3.1.1",
    // Database drivers
    "com.h2database" % "h2" % "1.4.192"
  )
}