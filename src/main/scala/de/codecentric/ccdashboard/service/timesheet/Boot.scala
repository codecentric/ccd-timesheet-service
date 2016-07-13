package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import de.codecentric.ccdashboard.service.timesheet.dataimport.DataImportActor
import de.codecentric.ccdashboard.service.timesheet.rest.MyServiceActor
import spray.can.Http

import scala.concurrent.duration._

/**
  * Created by bjacobs on 12.07.16.
  */
object Boot extends App with StrictLogging {
  logger.info("Starting up...")

  val conf = ConfigFactory.load()

  /*
    // Set-up demo data in the database
    val db = Database.forConfig("h2mem1")
    val dummyTable = TableQuery[DummyData]
    val dbSetup = DBIO.seq(
      dummyTable.schema.create,
      dummyTable ++= Seq(
        ("A", 42, 180.5),
        ("B", 32, 176.5),
        ("C", 60, 160.0)
      )
    )
    val dbSetupFuture = db.run(dbSetup)
    Await.result(dbSetupFuture, Duration.Inf)
  */

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("timesheet-actor-system")

  // create and start our rest service actor
  val restService = system.actorOf(Props[MyServiceActor], "rest-service")

  // create and start our data importer actor
  val dataImporter = system.actorOf(Props[DataImportActor], "data-importer")

  // Start the services
  dataImporter ! Start

  implicit val timeout = Timeout(5.seconds)
  val interface = conf.getString("timesheet-service.interface")
  val port = conf.getInt("timesheet-service.rest-port")
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(restService, interface, port)

  // Test
  dataImporter ! "say"
}

// Command case classes
case class Start()

case class Stop()
