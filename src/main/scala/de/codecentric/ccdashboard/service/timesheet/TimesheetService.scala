package de.codecentric.ccdashboard.service.timesheet

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.pattern.ask
import akka.stream.{ActorMaterializer, Materializer}
import akka.util.Timeout
import com.datastax.driver.core.SocketOptions
import com.github.bjoernjacobs.csup.CsUp
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.access._
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataIngestActor
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.routing.CustomPathMatchers._
import de.codecentric.ccdashboard.service.timesheet.util._
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Success, Try}
import ch.megard.akka.http.cors.CorsDirectives._

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

  val statusActor = system.actorOf(Props[StatusActor], "status-actor")
  statusActor ! StatusNotification("TimesheetService", Map("status" -> "booting"))

  // if set, try to acquire connection to database and perform initialization
  if (conf.getBoolean("timesheet-service.initializeDatabase")) {
    Await.result(CsUp().init(), Duration.Inf)
  }

  val httpServerBinding = startUp

  sys.addShutdownHook({
    logger.info("*** Shutting down ***")
    httpServerBinding.flatMap(_.unbind())
    val termination = system.terminate()
    Await.ready(termination, 10.seconds)
  })

  /**
    * Main start-up function that creates the ActorSystem and Actors
    */
  def startUp(implicit ec: ExecutionContext, materializer: Materializer): Future[ServerBinding] = {
    val dbConfigKey = conf.getString("timesheet-service.database-config-key")
    val dbConfig = conf.getConfig(dbConfigKey)

    val socketOptions = new SocketOptions().setConnectTimeoutMillis(60000).setReadTimeoutMillis(60000)
    val cassandraContextConfig = new CassandraContextConfigWithSocketOptions(dbConfig, socketOptions)

    // create and start our main actors and components
    val dataImporter = system.actorOf(Props(new DataIngestActor(conf, cassandraContextConfig)), "data-importer")
    val dataProvider = system.actorOf(Props(new DataProviderActor(conf, cassandraContextConfig)), "data-provider")
    val workScheduleProvider = system.actorOf(Props(new WorkScheduleProviderActor(cassandraContextConfig)), "work-schedule-provider")
    val bindingFuture = Http().bindAndHandle(route(dataProvider,workScheduleProvider), interface, port)

    system.scheduler.schedule(5.seconds, 30.seconds, new Runnable {
      override def run(): Unit = {
        dataImporter ! StatusRequest(statusActor)
        dataProvider ! StatusRequest(statusActor)
        statusActor ! StatusNotification("TimesheetService",
          Map(
            "status" -> "running",
            "start time" -> new Date(system.startTime).toString,
            "uptime (s)" -> system.uptime.toString,
            "debug log enabled" -> system.log.isDebugEnabled.toString,
            "dead letters" -> system.mailboxes.deadLetterMailbox.numberOfMessages.toString
          ))
      }
    })

    // Start importing
    dataImporter ! Start

    logger.info(s"Server online at http://$interface:$port/")

    bindingFuture
  }

  /**
    * Defines the service endpoints
    */
  def route(dataProvider: ActorRef, workScheduleProvider: ActorRef)(implicit ec: ExecutionContext, materializer: Materializer): Route = cors() {
    import CirceSupport._
    import Directives._
    import io.circe.generic.auto._

    implicit val timeout = Timeout(60.seconds)

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
      get {
        pathEndOrSingleSlash {
          val query = (dataProvider ? UserQuery(username)).mapTo[UserQueryResult]
          complete(query)
        }
      } ~
        path("worklog") {
          get {
            parameters('from.as[Date].?, 'to.as[Date].?) { (from, to) =>
              val query = (dataProvider ? WorklogQuery(username, from, to)).mapTo[WorklogQueryResult]
              complete(query)
            }
          }
        } ~
        path("report") {
          get {
            parameters('from.as[Date].?, 'to.as[Date].?, 'type.as[String].?) { (from, to, aggregationTypeString) =>
              val aggregationType = aggregationTypeString
                .map(s => Try(ReportQueryAggregationType.withName(s)))
                .getOrElse(Success(ReportQueryAggregationType.MONTHLY))
                .get

              val query = (dataProvider ? UserReportQuery(username, from, to, aggregationType)).mapTo[ReportQueryResponse]
              complete(query)
            }
          }
        } ~
        path("workschedule") {
          parameters('year.as[Int].?) { (year) =>
            get {
              val query = (workScheduleProvider ? WorkScheduleQuery(username, year)).mapTo[WorkScheduleQueryResult]
              complete(query)
            }
          }
        }
    } ~
      pathPrefix("issue" / issueIdMatcher) { id =>
        get {
          pathEndOrSingleSlash {
            val query = (dataProvider ? IssueQuery(id)).mapTo[IssueQueryResult]
            complete(query)
          }
        }
      } ~
      // This endpoint is meant to replace the team endpoint.
      pathPrefix("team") {
        path(IntNumber.?) { id =>
          get {
            pathEndOrSingleSlash {
              id match {
                case Some(teamId) =>
                  complete((dataProvider ? SingleTeamMembershipQuery(teamId)).mapTo[SingleTeamMembershipQueryResponse].map(_.team))
                case None =>
                  complete((dataProvider ? AllTeamMembershipQuery()).mapTo[AllTeamMembershipQueryResponse].map(_.teams))
              }
            }
          }
        } ~
          path(IntNumber / "report") { id =>
            get {
              parameters('from.as[Date].?, 'to.as[Date].?, 'type.as[String].?) { (from, to, aggregationTypeString) =>
                val aggregationType = aggregationTypeString
                  .map(s => Try(ReportQueryAggregationType.withName(s)))
                  .getOrElse(Success(ReportQueryAggregationType.MONTHLY))
                  .get

                val query = (dataProvider ? TeamReportQuery(id, from, to, aggregationType)).mapTo[ReportQueryResponse]
                complete(query)
              }
            }
          }
      } ~
      pathPrefix("employees") {
        get {
          pathEndOrSingleSlash {
            val query = (dataProvider ? EmployeesQuery()).mapTo[EmployeesQueryResponse].map(_.employees)
            complete(query)
          }
        }
      } ~
      pathPrefix("status") {
        get {
          val query = (statusActor ? StatusQuery).mapTo[StatusQueryResponse]

          pathEndOrSingleSlash {
            complete(query.map(_.statusMap))
          } ~
            pathSuffix("q") {
              complete(query.map(_.importCompleted))
          }
        }
      }
  }
}