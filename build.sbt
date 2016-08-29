// Basic project configuration
lazy val root = (project in file(".")).
  settings(
    name := "ccd-timesheet-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// Project dependencies
libraryDependencies ++= {
  val akkaV = "2.4.9"
  val circeV = "0.5.0-M3"
  val quillV = "0.9.1-SNAPSHOT"


  Seq(
    //  groupID %% artifactID % revision
    // Actors, rest server and client
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    // Prevent warning
    "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
    // Logging
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    // Data-access
    "io.getquill" %% "quill-core" % quillV,
    "io.getquill" %% "quill-cassandra" % quillV,
    // Database drivers
    "com.h2database" % "h2" % "1.4.192",
    // Testing
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaV,
    // OAuth
    "com.github.scribejava" % "scribejava-core" % "2.8.1",
    // JSON
    "io.circe" %% "circe-core" % circeV,
    "io.circe" %% "circe-generic" % circeV,
    "io.circe" %% "circe-parser" % circeV,
    "io.circe" %% "circe-java8" % circeV,
    "io.circe" %% "circe-jawn" % circeV
  )
}

//mainClass in (Compile, run) := Some("de.codecentric.ccdashboard.service.timesheet.dataimport.ImportTest")