package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.{LocalDate, LocalDateTime}
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
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.messages.{JiraIssueDetailsQueryTask, JiraTempoTeamMembersQueryTask, JiraTempoTeamQueryTask, _}
import de.codecentric.ccdashboard.service.timesheet.util.{StatusNotification, StatusRequest}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.java8.time._

import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.{Failure, Success}

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
  val importSyncInterval = FiniteDuration(conf.getDuration("timesheet-service.data-import.sync-interval").toMinutes, TimeUnit.MINUTES)
  val importWaitBetweenBatches = {
    val v = conf.getDuration("timesheet-service.data-import.wait-between-batches")
    FiniteDuration(v.toNanos, TimeUnit.NANOSECONDS)
  }
  val importEndDate = importStartDate.plusDays(importBatchSizeDays)

  // A few indicators or counters
  var completedUsersImportOnce = false
  var completedTeamsImportOnce = false
  var completedWorklogsImportOnce = false
  var lastRead: Option[LocalDateTime] = None

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
      context.system.scheduler.scheduleOnce(1.seconds, self, JiraUserQueryTask())

      // Query tempo teams
      context.system.scheduler.scheduleOnce(2.seconds, self, JiraTempoTeamQueryTask)

    /**
      * Handle the different query messages
      */
    case q@TempoWorklogQueryTask(toDate, fromDate, syncing) =>
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
              completedWorklogsImportOnce = true
              TempoWorklogQueryTask(now, now.minusDays(importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(importBatchSizeDays), syncing = false)
            }
          }
          lastRead = Some(LocalDateTime.now())
          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, nextImport)
        },
        // Error handler
        ex => {
          log.error(ex, s"TempoWorklogQueryTask task failed. Rescheduling in $importWaitBetweenBatches")
          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, q)
        })

    case q@JiraUserQueryTask() =>
      log.debug("Jira user query task received.")

      val ccUsersPromise = Promise[List[User]]
      val instanaUsersPromise = Promise[List[User]]

      Seq(
        (ccUsersPromise, "codecentric.de"),
        (instanaUsersPromise, "instana.com")
      ).foreach {
        case (promise, mailSuffix) =>
          val uri = getJiraUsersRequestUri(mailSuffix)

          handleRequest(uri, signRequest = true,
            // Success handler
            jsonEntityHandler(_)(jsonString => {
              //log.info(s"Received jsonString $jsonString")
              decode[List[JiraUser]](jsonString) match {
                case Xor.Left(error) =>
                  promise.failure(error)
                  log.error(error, "Could not decode users.")
                case Xor.Right(jiraUsers) =>
                  log.debug(s"Received ${jiraUsers.size} user")

                  val users = jiraUsers.map(_.toUser)
                  promise.success(users)
              }
            }),
            // Error handler
            ex => {
              promise.failure(ex)
            })
      }

      val allUsers = for {
        ccUsers <- ccUsersPromise.future
        instanaUsers <- instanaUsersPromise.future
      } yield Users(ccUsers ++ instanaUsers)

      allUsers.onComplete {
        case Success(users) =>
          dataWriter ! users

          lastRead = Some(LocalDateTime.now())
          completedUsersImportOnce = true

          log.info(s"Scheduling next users-import in $importSyncInterval")
          context.system.scheduler.scheduleOnce(importSyncInterval, self, q)

        case Failure(ex) =>
          log.error(ex, s"Users-import failed. Rescheduling next users-import in $importWaitBetweenBatches")
          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, q)
      }

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
              lastRead = Some(LocalDateTime.now())
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"JiraIssueDetailsQueryTask task failed")
        })

    case q@JiraTempoTeamQueryTask =>
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
              lastRead = Some(LocalDateTime.now())
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"JiraTempoTeamQueryTask task failed. Rescheduling in $importWaitBetweenBatches")
          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, q)
        })

    case q@JiraTempoTeamMembersQueryTask((teamId :: remainingTeamIds)) =>
      log.debug("Jira Tempo Team Members task received.")
      val queryUri = getJiraTempoTeamMembersUri(teamId)
      log.debug(s"Using query URI: $queryUri")
      handleRequest(queryUri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          decode[Seq[JiraTempoTeamMember]](jsonString) match {
            case Xor.Left(error) =>
              log.error(error, s"Failed to decode team members for team $teamId")
            case Xor.Right(jiraTempoTeamMembers) =>
              // Inject teamId after parsing since response does not contain it
              val teamMembers = jiraTempoTeamMembers.map(_.toTeamMember).toList
              dataWriter ! TeamMemberships(teamId, teamMembers)
              lastRead = Some(LocalDateTime.now())
          }

          if (remainingTeamIds.isEmpty) {
            log.info(s"Scheduling next Teams import in $importSyncInterval")
            context.system.scheduler.scheduleOnce(importSyncInterval, self, JiraTempoTeamQueryTask)
            completedTeamsImportOnce = true
          } else {
            log.debug(s"Scheduling team query for next team in 3 second")
            context.system.scheduler.scheduleOnce(3.seconds, self, JiraTempoTeamMembersQueryTask(remainingTeamIds))
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"Team members query for team $teamId failed. Rescheduling im $importWaitBetweenBatches")
          context.system.scheduler.scheduleOnce(importWaitBetweenBatches, self, q)
        })

    case StatusRequest(statusActor) =>
      val List(usersImportStatus, teamsImportStatus, worklogsImportStatus) =
        List(completedUsersImportOnce, completedTeamsImportOnce, completedWorklogsImportOnce)
          .map(f => if (f) "syncing" else "importing")

      statusActor ! StatusNotification("JiraDataReader", Map(
        "users import status" -> usersImportStatus,
        "teams import status" -> teamsImportStatus,
        "worklogs import status" -> worklogsImportStatus,
        "last read" -> lastRead.map(_.toString).getOrElse("")))
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

  def getJiraUsersRequestUri(mailSuffix: String) = {
    val path = Uri.Path(jiraUsersServicePath)
    val queryString = Query(Map(
      "username" -> mailSuffix,
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

