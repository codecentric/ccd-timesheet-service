package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import cats.data.Xor
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.jira._
import de.codecentric.ccdashboard.service.timesheet.data.model.{TeamMemberships, Teams, Users, Worklogs}
import de.codecentric.ccdashboard.service.timesheet.messages.{JiraIssueDetailsQueryTask, JiraTempoTeamMembersQueryTask, JiraTempoTeamQueryTask, _}
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

  override def scheme = jiraConf.getString("scheme")

  override def host = jiraConf.getString("host")

  override def authority = Uri.Authority(Uri.Host(host))

  val jiraUsersServicePath = jiraConf.getString("get-users-service-path")
  val jiraIssueDetailsServicePath = jiraConf.getString("get-issue-details-service-path")
  val jiraTempoTeamServicePath = jiraConf.getString("get-tempo-team-service-path")
  val jiraTempoTeamMembersServicePath = jiraConf.getString("get-tempo-team-members-service-path")
  val jiraTempoPath = jiraConf.getString("tempo.service-path")

  val accessToken = jiraConf.getString("access-token")
  val tempoApiToken = jiraConf.getString("tempo.api-token")
  val consumerPrivateKey = jiraConf.getString("consumer-private-key")

  val timesheetProjectKey = conf.getString("timesheet-service.data-import.project-key")
  val importStartDate = LocalDate.parse(conf.getString("timesheet-service.data-import.start-date"))
  val importBatchSizeDays = conf.getDuration("timesheet-service.data-import.batch-size").toDays
  val importSyncRangeDays = conf.getDuration("timesheet-service.data-import.sync-range").toDays
  val importWaitBetweenBatches = {
    val v = conf.getDuration("timesheet-service.data-import.wait-between-batches")
    FiniteDuration(v.toNanos, TimeUnit.NANOSECONDS)
  }
  val importEndDate = importStartDate.plusDays(importBatchSizeDays)

  val alphabet = ('a' to 'z').toList

  import context.dispatcher

  log.debug(s"Instantiated with: $scheme, $host, $jiraTempoPath, $accessToken, $tempoApiToken, $consumerPrivateKey, $importStartDate, $importEndDate")

  def receive = {
    /**
      * Perform startup and send initial request
      */
    case Start =>
      log.info("Received Start message -> commencing to query Jira")
      // Start Tempo Worklog Query async
      val now = LocalDate.now()
      context.system.scheduler.scheduleOnce(0.seconds, self, TempoWorklogQueryTask(now, now.minusDays(importBatchSizeDays), syncing = false))

      // Start Jira User Queries async
      context.system.scheduler.scheduleOnce(0.seconds, self, JiraUserQueryTask(0, 0))

      // Query tempo teams
      context.system.scheduler.scheduleOnce(0.seconds, self, JiraTempoTeamQueryTask)

    /**
      * Handle the different query messages
      */
    case TempoWorklogQueryTask(toDate, fromDate, syncing) =>
      log.debug("Tempo query task received.")

      val queryUri = getWorklogRequestUri(fromDate, toDate)
      log.debug(s"Using query: $queryUri")

      handleRequest(queryUri, signRequest = false,
        // Success handler
        entity => {
          implicit val um = jiraWorklogUnmarshaller
          val worklogsFuture =
            Unmarshal(entity).to[List[JiraWorklog]]
              .map(_.map(_.toWorklog))
              .map(Worklogs)

          // TODO: Filter internal issue IDs?
          worklogsFuture.foreach(worklogs => {
            worklogs.content.map(_.issueKey).distinct.filter(_ => true).foreach(issueId =>
              self ! JiraIssueDetailsQueryTask(Left[String, Int](issueId))
            )
          })

          worklogsFuture.pipeTo(dataWriter)

          // Determine which task to query when
          val now = LocalDate.now()
          val nextImport = if (syncing) {
            if (fromDate.isEqual(now.minusDays(importSyncRangeDays))) {
              TempoWorklogQueryTask(now, now.minusDays(importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(importBatchSizeDays), syncing = true)
            }
          } else {
            if (fromDate.isEqual(importStartDate)) {
              TempoWorklogQueryTask(now, now.minusDays(importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(importBatchSizeDays), syncing = false)
            }
          }

          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, nextImport)
        },
        // Error handler
        ex => {
          log.error(s"TempoWorklogQueryTask task failed because of query error '${ex.getMessage}'")
        })

    case JiraUserQueryTask(iteration, charIndex) =>
      log.debug("Jira user query task received.")

      val currentChar = alphabet(charIndex)
      val uri = getJiraUsersRequestUri(currentChar)

      handleRequest(uri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          //log.info(s"Received jsonString $jsonString")
          decode[List[JiraUser]](jsonString) match {
            case Xor.Left(error) =>
              log.error(error, "Could not decode users.")
            case Xor.Right(jiraUsers) =>
              log.debug(s"Received ${jiraUsers.size} user")
              val users = jiraUsers.map(_.toUser)
              dataWriter ! Users(users)

              if (charIndex == alphabet.size - 1) {
                log.info("Scheduling new users-query iteration in 3600 seconds")
                context.system.scheduler.scheduleOnce(3600.seconds, self, JiraUserQueryTask(iteration + 1, 0))
              } else {
                log.debug("Scheduling next user query in 10 seconds")
                context.system.scheduler.scheduleOnce(10.seconds, self, JiraUserQueryTask(iteration, charIndex + 1))
              }
          }
        }),
        // Error handler
        ex => {
          log.error(s"JiraUserQueryTask task failed because of query error '${ex.getMessage}'")
        })

    case JiraIssueDetailsQueryTask(issueId: Either[String, Int]) =>
      log.debug(s"Querying issue details task for issue id $issueId received.")
      val queryUri = getJiraIssueDetailsUri(issueId)
      handleRequest(queryUri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          decode[JiraIssue](jsonString) match {
            case Xor.Left(error) =>
              log.error(error, s"Could not decode issue $issueId")
            case Xor.Right(jiraIssue) =>
              val issue = jiraIssue.toIssue
              dataWriter ! issue
          }
        }),
        // Error handler
        ex => {
          log.error(s"JiraIssueDetailsQueryTask task failed because of query error '${ex.getMessage}'")
        })

    case JiraTempoTeamQueryTask =>
      log.debug("Jira Tempo Team task received.")
      val queryUri = getJiraTempoTeamsUri
      handleRequest(queryUri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          decode[Seq[JiraTempoTeam]](jsonString) match {
            case Xor.Left(error) => println(error)
            case Xor.Right(jiraTempoTeams) =>
              dataWriter ! Teams(jiraTempoTeams.map(_.toTeam).toList)
              val teamIds = jiraTempoTeams.map(_.id).toList
              self ! JiraTempoTeamMembersQueryTask(teamIds)
          }
        }),
        // Error handler
        ex => {
          log.error(s"JiraTempoTeamQueryTask task failed because of query error '${ex.getMessage}'")
        })

    case JiraTempoTeamMembersQueryTask((teamId :: remainingTeamIds)) =>
      log.debug("Jira Tempo Team Members task received.")
      val queryUri = getJiraTempoTeamMembersUri(teamId)
      log.debug(s"Using query URI: $queryUri")
      handleRequest(queryUri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          decode[Seq[JiraTempoTeamMember]](jsonString) match {
            case Xor.Left(error) => println(error)
            case Xor.Right(jiraTempoTeamMembers) =>
              // Inject teamId after parsing since response does not contain it
              val teamMembers = jiraTempoTeamMembers.map(_.toTeamMember).toList
              dataWriter ! TeamMemberships(teamId, teamMembers)
          }

          if (remainingTeamIds.isEmpty) {
            log.info("Scheduling next Teams query iteration in 3600 seconds")
            context.system.scheduler.scheduleOnce(3600.seconds, self, JiraTempoTeamQueryTask)
          } else {
            log.debug(s"Scheduling team query for next team in 1 second")
            context.system.scheduler.scheduleOnce(1.seconds, self, JiraTempoTeamMembersQueryTask(remainingTeamIds))
          }
        }),
        // Error handler
        ex => {
          log.error(s"JiraTempoTeamMembersQueryTask task failed because of query error '${ex.getMessage}'")
        })
  }

  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val queryString = Query(Map(
      "dateFrom" -> fromDate.toString,
      "dateTo" -> toDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> tempoApiToken,
      "projectKey" -> timesheetProjectKey)).toString

    val path = Uri.Path(jiraTempoPath)

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getJiraUsersRequestUri(currentChar: Char) = {
    val path = Uri.Path(jiraUsersServicePath)
    val queryString = Query(Map(
      "username" -> currentChar.toString,
      "maxResults" -> "100000")).toString

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getJiraIssueDetailsUri(issueId: Either[String, Int]) = {
    val issueIdString = issueId match {
      case Left(key) => key
      case Right(id) => id.toString
    }
    val path = Uri.Path(jiraIssueDetailsServicePath) / issueIdString
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamsUri = {
    val path = Uri.Path(jiraTempoTeamServicePath)
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamMembersUri(teamId: Int) = {
    val path = Uri.Path(jiraTempoTeamMembersServicePath.format(teamId.toString))
    Uri(scheme = scheme, authority = authority, path = path)
  }
}

