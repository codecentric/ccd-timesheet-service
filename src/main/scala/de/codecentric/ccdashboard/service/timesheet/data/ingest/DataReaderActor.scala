package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.data.marshalling.Unmarshallers
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import de.codecentric.ccdashboard.service.timesheet.data.source.jira.JiraWorklogs
import de.codecentric.ccdashboard.service.timesheet.messages.Start

import scala.concurrent.Future

/**
  * Created by bjacobs on 13.07.16.
  */
trait DataReaderActor extends Actor with ActorLogging

abstract class BaseDataReaderActor(val dataWriter: ActorRef) extends DataReaderActor {

  import context.dispatcher

  def pipeToWriter(f: Future[Worklogs]) = {
    import akka.pattern.pipe
    f.pipeTo(dataWriter)
  }
}

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

  val http = Http(context.system)

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  import akka.pattern.pipe
  import context.dispatcher

  log.info(s"Instantiated with: $jiraScheme, $jiraHost, $jiraTempoPath, $accessToken, $tempoApiToken, $consumerPrivateKey, $importStartDate, $importEndDate")

  def receive = {
    /**
      * Perform startup and send initial request
      */
    case Start =>
      log.info("Received message. Querying tempo...")
      val queryUri = getWorklogRequestUri(importStartDate, importEndDate)
      log.info(s"Using query: $queryUri")
      http.singleRequest(HttpRequest(method = HttpMethods.GET, uri = queryUri)).pipeTo(self)

    /**
      * When request completes, unmarshall result and pipe to dataWriter
      */
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      log.info("Received response")
      implicit val um = Unmarshallers.jiraWorklogsUnmarshaller
      val jiraWorklogsFuture = Unmarshal(entity).to[JiraWorklogs]
      val worklogsFuture = jiraWorklogsFuture.map(_.jiraWorklogs).map(_.map(_.toWorklog)).map(Worklogs)
      pipeToWriter(worklogsFuture)

    case HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
  }

  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val queryString = Query(Map(
      "dateFrom" -> importStartDate.toString,
      "dateTo" -> importEndDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> tempoApiToken)).toString

    val authority = Uri.Authority(Uri.Host(jiraHost))
    val path = Uri.Path(jiraTempoPath)

    Uri(scheme = jiraScheme, authority = authority, path = path, queryString = Some(queryString), None)
  }
}
