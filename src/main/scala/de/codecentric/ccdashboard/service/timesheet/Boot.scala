package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import de.codecentric.ccdashboard.service.timesheet.dataimport.DataImportActor
import de.codecentric.ccdashboard.service.timesheet.rest.MyServiceActor
import spray.can.Http

import scala.concurrent.duration._

/**
  * Created by bjacobs on 12.07.16.
  */
object Boot extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our rest service actor
  val restService = system.actorOf(Props[MyServiceActor], "rest-service")

  // create and start our data importer actor
  val dataImporter = system.actorOf(Props[DataImportActor], "data-importer")

  // Start the services
  dataImporter ! "Start"

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(restService, interface = "localhost", port = 8080)
}
