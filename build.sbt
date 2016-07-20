// Basic project configuration
lazy val root = (project in file(".")).
  settings(
    name := "ccd-timesheet-service",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.11.8"
  )

// Project dependencies
libraryDependencies ++= {
  val akkaV = "2.4.8"
  val slickV = "3.1.1"

  Seq(
    //  groupID %% artifactID % revision
    // Actors, rest server and client
    "com.typesafe.akka" %% "akka-http-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    // Prevent warning
    "org.scala-lang.modules" %% "scala-xml" % "1.0.4",
    // Logging
    "ch.qos.logback" % "logback-classic" % "1.1.7",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.4.0",
    // Data-access
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    // Database drivers
    "com.h2database" % "h2" % "1.4.192",
    // Testing
    "org.scalatest" %% "scalatest" % "2.2.6" % "test",
    "com.typesafe.akka" %% "akka-testkit" % akkaV
  )
}

//mainClass in (Compile, run) := Some("de.codecentric.ccdashboard.service.timesheet.dataimport.ImportTest")