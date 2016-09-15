// Basic project configuration
lazy val root = (project in file(".")).
  enablePlugins(JavaAppPackaging).
  settings(
    name := "ccd-timesheet-service",
    organization := "de.codecentric",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.bintrayRepo("hseeberger", "maven")
)

// Project dependencies
libraryDependencies ++= {
  val akkaV = "2.4.10"
  val circeV = "0.5.1"
  val quillV = "0.10.0"

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
    "io.circe" %% "circe-jawn" % circeV,
    "de.heikoseeberger" %% "akka-http-circe" % "1.9.0",
    // Cassandra initialization
    "com.github.bjoernjacobs" %% "csup" % "0.0.5-SNAPSHOT"
  )
}

import NativePackagerHelper._
// Packaging settings
mainClass in Compile := Some("de.codecentric.ccdashboard.service.timesheet.TimesheetService")
topLevelDirectory := None
packageName in Universal := "ccd-timesheet-service"

// mappings in Universal ++= directory("src/main/resources/sql")