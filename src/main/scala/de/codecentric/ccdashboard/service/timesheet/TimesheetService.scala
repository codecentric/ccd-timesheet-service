package de.codecentric.ccdashboard.service.timesheet

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataIngestActor
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.routing.CustomPathMatchers._
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
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
  implicit val executionContext = system.dispatcher

  val logger = Logging.getLogger(system, this)

  // create and start our main actors and components
  val dataImporter = system.actorOf(Props(new DataIngestActor(conf)), "data-importer")
  val dataProvider = system.actorOf(Props(new DataProviderActor(conf)), "data-provider")
  val bindingFuture = Http().bindAndHandle(route, interface, port)

  // Start importing
  dataImporter ! Start

  println(s"Server online at http://$interface:$port/")

  // let it run until user presses return
  Future {
    StdIn.readLine("Press [RETURN] to stop server")
  }.onComplete { _ =>
    logger.info("Shutting down TimesheetService by user request")
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
  }

  /**
    * Defines the service endpoints
    */
  def route(implicit ec: ExecutionContext, materializer: Materializer) = {
    import CirceSupport._
    import Directives._
    import io.circe.generic.auto._

    implicit val timeout = Timeout(15.seconds)

    /* Akka HTTP Unmarshallers */
    implicit val localDateUnmarshaller = Unmarshaller[String, LocalDate] { ex => str =>
      Future {
        LocalDate.parse(str)
      }
    }

    implicit val dateUnmarshaller = Unmarshaller[String, Date] { ex => str =>
      Future {
        new SimpleDateFormat("yyyy-MM-dd").parse(str)
      }
    }

    pathPrefix("user" / usernameMatcher) { username =>
      logger.info(s"Username ist: $username")
      pathEndOrSingleSlash {
        val query = (dataProvider ? UserQuery(username)).mapTo[UserQueryResult]
        onComplete(query) {
          case Success(res) => complete(res.user)
          case Failure(ex) => failWith(ex)
        }
      } ~
        path("worklog") {
          get {
            parameters('from.as[Date].?, 'to.as[Date].?) { (from, to) =>
              val query = (dataProvider ? WorklogQuery(username, from, to)).mapTo[WorklogQueryResult]
              onComplete(query) {
                case Success(res) => complete(res.worklogs)
                case Failure(ex) => failWith(ex)
              }
            }
          }
        }
    } ~
      pathPrefix("issue" / issueIdMatcher) { id =>
        pathEndOrSingleSlash {
          val query = (dataProvider ? IssueQuery(id)).mapTo[IssueQueryResult]
          onComplete(query) {
            case Success(res) => complete(res.issue)
            case Failure(ex) => failWith(ex)
          }
        }
      }
  }
}