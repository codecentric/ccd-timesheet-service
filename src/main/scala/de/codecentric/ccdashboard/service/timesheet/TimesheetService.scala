package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataIngestActor
import de.codecentric.ccdashboard.service.timesheet.messages.{Start, WorklogQuery, WorklogQueryResult}
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

/**
  * Created by bjacobs on 12.07.16.
  */
object TimesheetService extends App {
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
  val dataProvider = system.actorOf(Props(new DataProviderActor(conf)), "data-provider")
  val bindingFuture = Http().bindAndHandle(route, interface, port)

  // Start importing
  dataImporter ! Start

  println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate())

  // and shutdown when done

  /**
    * Defines the service endpoints
    */
  def route(implicit ec: ExecutionContext, materializer: Materializer) = {
    import CirceSupport._
    import Directives._
    import io.circe.generic.auto._

    implicit val timeout = Timeout(15.seconds)

    path("getWorklogs") {
      get {
        val query = (dataProvider ? WorklogQuery(3)).mapTo[WorklogQueryResult]
        onComplete(query) {
          case Success(res) => complete(res.worklogs)
          case Failure(ex) => complete(ex)
        }
      }
    }
  }
}