package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.data.marshalling.json.MasterJsonProtocol._
import de.codecentric.ccdashboard.service.timesheet.data.marshalling.xml.Unmarshallers
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import de.codecentric.ccdashboard.service.timesheet.data.source.jira._
import de.codecentric.ccdashboard.service.timesheet.messages.Start

import scala.concurrent.duration._

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

class JiraDataReaderActor(conf: Config, dataWriter: ActorRef) extends BaseDataReaderActor(dataWriter) {

  val jiraConf = ConfigFactory.load("jiraclient.conf").getConfig("jira")

  override def getOAuthConfig = Some(jiraConf)

  val jiraScheme = jiraConf.getString("scheme")
  val jiraHost = jiraConf.getString("host")
  val jiraUsersServicePath = jiraConf.getString("get-users-service-path")
  val jiraIssueDetailsServicePath = jiraConf.getString("get-issue-details-service-path")
  val jiraTempoTeamServicePath = jiraConf.getString("get-tempo-team-service-path")
  val jiraTempoPath = jiraConf.getString("tempo.service-path")
  val jiraAuthority = Uri.Authority(Uri.Host(jiraHost))

  val accessToken = jiraConf.getString("access-token")
  val tempoApiToken = jiraConf.getString("tempo.api-token")
  val consumerPrivateKey = jiraConf.getString("consumer-private-key")

  val importStartDate = LocalDate.parse(conf.getString("timesheet-service.data-import.start-date"))
  val importBatchSize = conf.getDuration("timesheet-service.data-import.batch-size")
  val importEndDate = importStartDate.plusDays(importBatchSize.toDays)

  val alphabet = ('a' to 'z').toList

  import context.dispatcher

  log.info(s"Instantiated with: $jiraScheme, $jiraHost, $jiraTempoPath, $accessToken, $tempoApiToken, $consumerPrivateKey, $importStartDate, $importEndDate")

  def receive = {
    /**
      * Perform startup and send initial request
      */
    case Start =>
      // Start Tempo Query async
      context.system.scheduler.scheduleOnce(1.seconds, self, TempoQueryTask)

    // Start Jira Queries async
    //context.system.scheduler.scheduleOnce(2.seconds, self, JiraUserQueryTask(0, 0))

    // Query one issue
    //context.system.scheduler.scheduleOnce(1.seconds, self, JiraIssueDetailsQueryTask(Left("CCD-36")))

    // Query tempo teams
    //context.system.scheduler.scheduleOnce(1.seconds, self, JiraTempoTeamQueryTask)

    case TempoQueryTask =>
      log.info("Tempo query task received.")

      val queryUri = getWorklogRequestUri(importStartDate, importEndDate)
      log.info(s"Using query: $queryUri")

      handleRequest(queryUri, signRequest = false, entity => {
        implicit val um = Unmarshallers.jiraWorklogUnmarshaller
        val worklogsFuture =
          Unmarshal(entity).to[Seq[JiraWorklog]]
            .map(_.map(_.toWorklog))
            .map(Worklogs)

        worklogsFuture.pipeTo(dataWriter)
      })

    case JiraUserQueryTask(iteration, charIndex) =>
      log.info("Jira query task received.")

      val currentChar = alphabet(charIndex)
      val uri = getJiraUsersRequestUri(currentChar)

      handleRequest(uri, signRequest = true, jsonEntityHandler(_)(jsonAST => {
        val users = jsonAST.convertTo[Seq[User]]

        log.info(s"Received ${users.size} user")
        // TODO store to database using writerActor

        if (charIndex == alphabet.size - 1) {
          log.info("Scheduling new iteration in 3600 seconds")
          context.system.scheduler.scheduleOnce(3600.seconds, self, JiraUserQueryTask(iteration + 1, 0))
        } else {
          log.info("Scheduling next user query in 10 seconds")
          context.system.scheduler.scheduleOnce(10.seconds, self, JiraUserQueryTask(iteration, charIndex + 1))
        }
      }))

    case JiraIssueDetailsQueryTask(issueId: Either[String, Int]) =>
      log.info(s"Querying issue details task for issue id $issueId received.")
      val queryUri = getJiraIssueDetailsUri(issueId)
      handleRequest(queryUri, signRequest = true, jsonEntityHandler(_)(jsonAST => {
        val issue = jsonAST.convertTo[JiraIssue]
        println(issue)
      }))

    case JiraTempoTeamQueryTask =>
      log.info("Jira Tempo Team task received.")
      val queryUri = getJiraTempoTeamsUri
      handleRequest(queryUri, signRequest = true, jsonEntityHandler(_)(jsonAST => {
        val teams = jsonAST.convertTo[Seq[JiraTempoTeam]]
        teams.foreach(println)
      }))
  }


  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val queryString = Query(Map(
      "dateFrom" -> importStartDate.toString,
      "dateTo" -> importEndDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> tempoApiToken)).toString

    val path = Uri.Path(jiraTempoPath)

    Uri(scheme = jiraScheme, authority = jiraAuthority, path = path, queryString = Some(queryString))
  }

  def getJiraUsersRequestUri(currentChar: Char) = {
    val path = Uri.Path(jiraUsersServicePath)
    val queryString = Query(Map(
      "username" -> currentChar.toString,
      "maxResults" -> "100000")).toString

    Uri(scheme = jiraScheme, authority = jiraAuthority, path = path, queryString = Some(queryString))
  }

  def getJiraIssueDetailsUri(issueId: Either[String, Int]) = {
    val issueIdString = issueId match {
      case Left(key) => key
      case Right(id) => id.toString
    }
    val path = Uri.Path(jiraIssueDetailsServicePath) / issueIdString
    Uri(scheme = jiraScheme, authority = jiraAuthority, path = path)
  }

  def getJiraTempoTeamsUri = {
    val path = Uri.Path(jiraTempoTeamServicePath)
    Uri(scheme = jiraScheme, authority = jiraAuthority, path = path)
  }
}

case class TempoQueryTask()

case class JiraUserQueryTask(iteration: Int, charIndex: Int)

case class JiraIssueDetailsQueryTask(issueId: Either[String, Int])

case class JiraTempoTeamQueryTask()

