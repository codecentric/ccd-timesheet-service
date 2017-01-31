import sbt._

object Version {
  final val Akka          = "2.4.16"
  final val AkkaHttp      = "10.0.1"
  final val AkkaHttpJson  = "1.9.0" // FIXME latest is 1.11.0
  final val AkkaLog4j     = "1.2.0"
  final val Circe         = "0.5.1" // FIXME latest is 0.6.1
  final val Csup          = "0.0.5-SNAPSHOT" // FIXME don't depend on snapshots
  final val Logback       = "1.1.7"
  final val Quill         = "1.0.1"
  final val Scala         = "2.12.0"
  final val ScalaLogging  = "3.4.0"
  final val ScalaTest     = "3.0.1"
  final val Scribe        = "2.8.1"
  final val ScalaXml      = "1.0.4"
  final val ScalaMock     = "3.4.2"
}

object Library {
  val akkaActor            = "com.typesafe.akka"          %% "akka-actor"                  % Version.Akka
  val akkaHttp             = "com.typesafe.akka"          %% "akka-http"                   % Version.AkkaHttp
  val akkaHttpCirce        = "de.heikoseeberger"          %% "akka-http-circe"             % Version.AkkaHttpJson
  val akkaHttpTestkit      = "com.typesafe.akka"          %% "akka-http-testkit"           % Version.AkkaHttp
  val akkaTestkit          = "com.typesafe.akka"          %% "akka-testkit"                % Version.Akka
  val circeCore            = "io.circe"                   %% "circe-core"                  % Version.Circe
  val circeGeneric         = "io.circe"                   %% "circe-generic"               % Version.Circe
  val circeJava8           = "io.circe"                   %% "circe-java8"                 % Version.Circe
  val circeParser          = "io.circe"                   %% "circe-parser"                % Version.Circe
  val csup                 = "com.github.bjoernjacobs"    %% "csup"                        % Version.Csup
  val logback              = "ch.qos.logback"             %  "logback-classic"             % Version.Logback
  val quillCore            = "io.getquill"                %% "quill-core"                  % Version.Quill
  val quillCassandra       = "io.getquill"                %% "quill-cassandra"             % Version.Quill
  val scalaLogging         = "com.typesafe.scala-logging" %% "scala-logging"               % Version.ScalaLogging
  val scalaMock            = "org.scalamock"              %% "scalamock-scalatest-support" % Version.ScalaMock
  val scalaTest            = "org.scalatest"              %% "scalatest"                   % Version.ScalaTest
  val scalaXml             = "org.scala-lang.modules"     %% "scala-xml"                   % Version.ScalaXml
  val scribe               = "com.github.scribejava"      %  "scribejava-core"             % Version.Scribe
}
