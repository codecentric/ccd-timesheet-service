package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataIngestActor
import de.codecentric.ccdashboard.service.timesheet.messages.Start
import de.codecentric.ccdashboard.service.timesheet.rest.DataServiceActor

import scala.io.StdIn

/**
  * Created by bjacobs on 12.07.16.
  */
object Boot extends App {
  val conf = ConfigFactory.load()
  val interface = conf.getString("timesheet-service.interface")
  val port = conf.getInt("timesheet-service.rest-port")

  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("timesheet-actor-system")

  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val logger = Logging.getLogger(system, this)

  // create and start our main actors and components
  val dataImporter = system.actorOf(Props(new DataIngestActor(conf)), "data-importer")
  val dataProvider = system.actorOf(Props(new DataProviderActor(conf)), "h2-data-provider")
  val route = new DataServiceActor(dataProvider).route
  val bindingFuture = Http().bindAndHandle(route, interface, port)

  // Start importing
  dataImporter ! Start

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}