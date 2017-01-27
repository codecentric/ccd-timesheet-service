// Basic project configuration
lazy val root = (project in file(".")).
  enablePlugins(JavaAppPackaging).
  settings(
    name := "ccd-timesheet-service",
    organization := "de.codecentric",
    version := "0.5.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

resolvers ++= Seq(
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.bintrayRepo("hseeberger", "maven")
)

// Project dependencies
libraryDependencies ++= Vector(
  Library.akkaHttp,
  Library.akkaActor,
  Library.akkaHttpCirce,
  Library.circeCore,
  Library.circeGeneric,
  Library.circeJava8,
  Library.circeParser,
  Library.csup,
  Library.logback,
  Library.scalaLogging,
  Library.scalaXml,
  Library.scribe,
  Library.quillCore,
  Library.quillCassandra,

  Library.akkaHttpTestkit % "test",
  Library.scalaTest       % "test"
)


import NativePackagerHelper._
// Packaging settings
mainClass in Compile := Some("de.codecentric.ccdashboard.service.timesheet.TimesheetService")
topLevelDirectory := None
packageName in Universal := "ccd-timesheet-service"

// mappings in Universal ++= directory("src/main/resources/sql")
