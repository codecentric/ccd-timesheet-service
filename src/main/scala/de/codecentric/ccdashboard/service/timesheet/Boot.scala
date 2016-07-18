package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataIngestActor
import de.codecentric.ccdashboard.service.timesheet.data.server.H2DataServerActor
import de.codecentric.ccdashboard.service.timesheet.rest.DataServiceActor
import spray.can.Http

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Created by bjacobs on 12.07.16.
  */
object Boot extends App {
  val conf = ConfigFactory.load()

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("timesheet-actor-system")

  val logger = Logging.getLogger(system, this)

  // create and start our rest service actor
  val restService = system.actorOf(Props[DataServiceActor], "rest-service")

  // create and start our data importer actor
  val dataImporter = system.actorOf(Props(new DataIngestActor(conf)), "data-importer")

  val dataServer = system.actorOf(Props(new H2DataServerActor(conf)), "h2-data-server")

  //  val deadLetterListener = system.actorOf(Props(classOf[DeadLetterListenerActor]))
  //  system.eventStream.subscribe(deadLetterListener, classOf[DeadLetter])

  // Start the services
  dataImporter ! Start

  implicit val timeout = Timeout(5.seconds)
  val interface = conf.getString("timesheet-service.interface")
  val port = conf.getInt("timesheet-service.rest-port")
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(restService, interface, port)

  import scala.concurrent.ExecutionContext.Implicits.global

  val f = Future {
    Thread.sleep(5000)
    logger.info("Now querying...")
    dataServer ! WorklogQuery(3)
  }

}

// Command case classes
case class Start()

case class Stop()

case class WorklogQuery(x: Int)
