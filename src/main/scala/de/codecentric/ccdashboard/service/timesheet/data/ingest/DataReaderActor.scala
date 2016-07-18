package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.Start
import de.codecentric.ccdashboard.service.timesheet.data.schema.Worklogs
import de.codecentric.ccdashboard.service.timesheet.data.source.jira.JiraWorklogs
import spray.client.pipelining._
import spray.http.Uri
import spray.http.Uri.Query

import scala.util.{Failure, Success}

/**
  * Created by bjacobs on 13.07.16.
  */
trait DataReaderActor extends Actor with ActorLogging

abstract class BaseDataReaderActor(val dataWriter: ActorRef) extends DataReaderActor

class JiraDataReaderActor(conf: Config, dataWriter: ActorRef) extends BaseDataReaderActor(dataWriter) {

  val jiraConf = ConfigFactory.load("jiraclient.conf")

  val jiraScheme = jiraConf.getString("jira.scheme")
  val jiraHost = jiraConf.getString("jira.host")
  val jiraTempoPath = jiraConf.getString("jira.tempo-service-path")
  val accessToken = jiraConf.getString("jira.access-token")
  val tempoApiToken = jiraConf.getString("jira.tempo-api-token")
  val consumerPrivateKey = jiraConf.getString("jira.consumer-private-key")

  val importStartDate = LocalDate.parse(conf.getString("timesheet-service.data-import.start-date"))
  val importBatchSize = conf.getDuration("timesheet-service.data-import.batch-size")
  val importEndDate = importStartDate.plusDays(importBatchSize.toDays)

  import context.dispatcher

  // Tempo request pipeline
  val pipeline = sendReceive ~> unmarshal[JiraWorklogs]

  log.info(s"Instantiated with: $jiraScheme, $jiraHost, $jiraTempoPath, $accessToken, $tempoApiToken, $consumerPrivateKey, $importStartDate, $importEndDate")

  def receive = {
    case Start =>
      log.info("Received message. Querying tempo...")

      val queryUri = getWorklogRequestUri(importStartDate, importEndDate)
      log.info(s"Using query: $queryUri")
      val responseFuture = pipeline(Get(queryUri))

      responseFuture onComplete {
        case Success(response) =>
          log.info(s"Received: ${response.jiraWorklogs.size}")

          val worklogs = response.jiraWorklogs.map(_.toWorklog)

          dataWriter ! Worklogs(worklogs)

        case Failure(error) =>
          log.error(error, "Error during request!")
      }
  }

  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val query = Query(Map(
      "dateFrom" -> importStartDate.toString,
      "dateTo" -> importEndDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> tempoApiToken))
    Uri.from(scheme = jiraScheme, host = jiraHost, path = jiraTempoPath, query = query)
  }
}
