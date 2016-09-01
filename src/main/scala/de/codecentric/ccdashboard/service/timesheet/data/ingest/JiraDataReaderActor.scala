package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import cats.data.Xor
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.jira._
import de.codecentric.ccdashboard.service.timesheet.data.model.{Users, Worklogs}
import de.codecentric.ccdashboard.service.timesheet.messages._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.java8.time._

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
  val jiraTempoTeamMembersServicePath = jiraConf.getString("get-tempo-team-members-service-path")
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
      // Start Tempo Worklog Query async
      context.system.scheduler.scheduleOnce(0.seconds, self, TempoWorklogQueryTask)

      // Start Jira User Queries async
      context.system.scheduler.scheduleOnce(1.seconds, self, JiraUserQueryTask(0, 0))

      // Query one issue
      context.system.scheduler.scheduleOnce(2.seconds, self, JiraIssueDetailsQueryTask(Left("CCD-36")))

    // Query tempo teams
    //context.system.scheduler.scheduleOnce(10.seconds, self, JiraTempoTeamQueryTask)

    // Query one tempo team
    //context.system.scheduler.scheduleOnce(15.seconds, self, JiraTempoTeamMembersQueryTask(15))

    case TempoWorklogQueryTask =>
      log.info("Tempo query task received.")

      val queryUri = getWorklogRequestUri(importStartDate, importEndDate)
      log.info(s"Using query: $queryUri")

      handleRequest(queryUri, signRequest = false, entity => {
        implicit val um = jiraWorklogUnmarshaller
        val worklogsFuture =
          Unmarshal(entity).to[Seq[JiraWorklog]]
            .map(_.map(_.toWorklog))
            .map(Worklogs)

        worklogsFuture.pipeTo(dataWriter)
      })

    case JiraUserQueryTask(iteration, charIndex) =>
      log.info("Jira user query task received.")

      val currentChar = alphabet(charIndex)
      val uri = getJiraUsersRequestUri(currentChar)

      handleRequest(uri, signRequest = true, jsonEntityHandler(_)(jsonString => {
        decode[Seq[JiraUser]](jsonString) match {
          case Xor.Left(error) => println(error)
          case Xor.Right(jiraUsers) =>
            log.info(s"Received ${jiraUsers.size} user")
            val users = jiraUsers.map(_.toUser)
            dataWriter ! Users(users)

            if (charIndex == alphabet.size - 1) {
              log.info("Scheduling new iteration in 3600 seconds")
              context.system.scheduler.scheduleOnce(3600.seconds, self, JiraUserQueryTask(iteration + 1, 0))
            } else {
              log.info("Scheduling next user query in 10 seconds")
              context.system.scheduler.scheduleOnce(10.seconds, self, JiraUserQueryTask(iteration, charIndex + 1))
            }
        }
      }))

    case JiraIssueDetailsQueryTask(issueId: Either[String, Int]) =>
      log.info(s"Querying issue details task for issue id $issueId received.")
      val queryUri = getJiraIssueDetailsUri(issueId)
      handleRequest(queryUri, signRequest = true, jsonEntityHandler(_)(jsonString => {
        decode[JiraIssue](jsonString) match {
          case Xor.Left(error) => println(error)
          case Xor.Right(jiraIssue) =>
            val issue = jiraIssue.toIssue
            dataWriter ! issue
        }
      }))

    case JiraTempoTeamQueryTask =>
      log.info("Jira Tempo Team task received.")
      val queryUri = getJiraTempoTeamsUri
      handleRequest(queryUri, signRequest = true, jsonEntityHandler(_)(jsonString => {
        decode[Seq[JiraTempoTeam]](jsonString) match {
          case Xor.Left(error) => println(error)
          case Xor.Right(jiraTempoTeams) => println(jiraTempoTeams)
        }
      }))

    case JiraTempoTeamMembersQueryTask(teamId) =>
      log.info("Jira Tempo Team Members task received.")
      val queryUri = getJiraTempoTeamMembersUri(teamId)
      log.info(s"Using query URI: $queryUri")
      handleRequest(queryUri, signRequest = true, jsonEntityHandler(_)(jsonString => {
        decode[Seq[JiraTempoTeamMember]](jsonString) match {
          case Xor.Left(error) => println(error)
          case Xor.Right(jiraTempoTeamMembers) => println(jiraTempoTeamMembers)
        }
        //val teamMembers = jsonAST.convertTo[Seq[JiraTempoTeamMember]]
        //teamMembers.foreach(println)
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

  def getJiraTempoTeamMembersUri(teamId: Int) = {
    val path = Uri.Path(jiraTempoTeamMembersServicePath.format(teamId.toString))
    Uri(scheme = jiraScheme, authority = jiraAuthority, path = path)
  }
}

