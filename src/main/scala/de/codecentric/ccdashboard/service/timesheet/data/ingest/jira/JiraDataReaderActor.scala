package de.codecentric.ccdashboard.service.timesheet.data.ingest.jira

import java.time.{LocalDate, LocalDateTime}
import java.util.Date

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import cats.data.Xor
import com.typesafe.config.{Config, ConfigFactory}
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.ingest.{BaseDataReaderActor, DataAggregationActor, PerformUtilizationAggregation}
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.data.model.jira._
import de.codecentric.ccdashboard.service.timesheet.messages.{JiraIssueDetailsQueryTask, JiraTempoTeamMembersQueryTask, JiraTempoTeamQueryTask, _}
import de.codecentric.ccdashboard.service.timesheet.util.{StatusNotification, StatusRequest}
import io.circe.generic.auto._
import io.circe.parser._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
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

  // Team-IDs to filter. 4 = codecentric ALL
  val filteredTeamIds = Set(4)

  val aggregationActor = context.actorOf(Props(new DataAggregationActor(conf, dataWriter)))

  val c = new JiraConfig(conf)

  // A few indicators or counters
  var completedUsersImportOnce = false
  var completedTeamsImportOnce = false
  var completedWorklogsImportOnce = false
  var lastRead: Option[LocalDateTime] = None

  import context.dispatcher

  log.debug(s"Instantiated with: $scheme, $host, ${c.jiraTempoWorklogsServicePath}, ${c.accessToken}, ${c.tempoApiToken}, ${c.consumerPrivateKey}, ${c.importStartDate}, ${c.importEndDate}")

  def receive = {
    /**
      * Perform startup and send initial request
      */
    case Start =>
      log.info("Received Start message -> commencing to query Jira")
      // Start Tempo Worklog Query async
      val now = LocalDate.now()
      context.system.scheduler.scheduleOnce(0.seconds, self, TempoWorklogQueryTask(now, now.minusDays(c.importBatchSizeDays), syncing = false))

      // Start Jira User Queries async
      context.system.scheduler.scheduleOnce(1.seconds, self, JiraUserQueryTask())

      // Query tempo teams
      context.system.scheduler.scheduleOnce(2.seconds, self, JiraTempoTeamQueryTask)

    /**
      * Handle the different query messages
      */
    case q@TempoWorklogQueryTask(toDate, fromDate, syncing) =>
      implicit val timeout = Timeout(60.seconds)

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

          val issueResponses = worklogsFuture
            .map(_.content.map(_.issueKey).distinct)
            .map(_.map(issueKey => (self ? JiraIssueDetailsQueryTask(Left[String, Int](issueKey))).mapTo[JiraIssueDetailsQueryTaskResponse]))
            .flatMap(list => Future.sequence(list))
            .map(_.map(_.issue))

          val userWorklogGroupFuture = worklogsFuture.map(_.content.groupBy(_.username))

          val enrichmentRequests = for {
            issues <- issueResponses
            userWorklogGroups <- userWorklogGroupFuture
          } yield {
            userWorklogGroups.map {
              case (username, worklogs) =>
                val worklogIssues = worklogs.map(_.issueKey).distinct
                val relevantIssues = issues.filter(i => worklogIssues.contains(i.issueKey))
                (username, worklogs, relevantIssues)
            }
          }

          enrichmentRequests.map(_.foreach(r => aggregationActor ! PerformUtilizationAggregation(r._1, r._2, r._3)))

          worklogsFuture.pipeTo(dataWriter)

          // Determine which task to query when
          val now = LocalDate.now()
          val nextImport = if (syncing) {
            if (fromDate.isBefore(now.minusDays(c.importSyncRangeDays))) {
              TempoWorklogQueryTask(now, now.minusDays(c.importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(c.importBatchSizeDays), syncing = true)
            }
          } else {
            if (fromDate.isBefore(c.importStartDate)) {
              completedWorklogsImportOnce = true
              TempoWorklogQueryTask(now, now.minusDays(c.importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(c.importBatchSizeDays), syncing = false)
            }
          }
          lastRead = Some(LocalDateTime.now())
          context.system.scheduler.scheduleOnce(c.importWaitBetweenBatches, self, nextImport)
        },
        // Error handler
        ex => {
          log.error(ex, s"TempoWorklogQueryTask task failed. Rescheduling in ${c.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(c.importWaitBetweenBatches, self, q)
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

      val userScheduleQuery = allUsers
        .map(_.content.map(_.name))
        .map(_.map(username => TempoUserScheduleQueryTask(username, c.importStartDate, LocalDate.now())))
        .map(_.foreach(task => self ! task))

      allUsers.onComplete {
        case Success(users) =>
          dataWriter ! users

          lastRead = Some(LocalDateTime.now())
          completedUsersImportOnce = true

          log.info(s"Scheduling next users-import in ${c.importSyncInterval}")
          context.system.scheduler.scheduleOnce(c.importSyncInterval, self, q)

        case Failure(ex) =>
          log.error(ex, s"Users-import failed. Rescheduling next users-import in ${c.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(c.importWaitBetweenBatches, self, q)
      }

    case TempoUserScheduleQueryTask(username, startDate, endDate) =>
      log.debug("Jira user schedules task received.")

      val jiraUserSchedulesPromise = Promise[JiraUserSchedules]
      val jiraUserAvailabilitiesFuture = retrieveUserAvailabilities(username, startDate, endDate)

      val queryUri = getJiraTempoUserScheduleUri(username, localDateEncoder.f(startDate), localDateEncoder.f(endDate))
      handleRequest(queryUri, signRequest = true,
        // Success handler
        jsonEntityHandler(_)(jsonString => {
          decode[JiraUserSchedules](jsonString) match {
            case Xor.Left(error) =>
              log.error(error, s"Could not decode Jira user schedule")
            case Xor.Right(jiraUserSchedules) =>
              jiraUserSchedulesPromise.success(jiraUserSchedules)

              lastRead = Some(LocalDateTime.now())
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"TempoUserScheduleQueryTask task failed")
        })

      for {
        schedules <- jiraUserSchedulesPromise.future
        availabilites <- jiraUserAvailabilitiesFuture
      } yield {
        val mostRecentAvailablilityValue = availabilites
          .toUserAvailabilities
          .content
          .sortWith((l, r) => l.dateFrom.isAfter(r.dateFrom))
          .headOption
          .map(_.availability)

        if (mostRecentAvailablilityValue.isEmpty) {
          log.info(s"No availability value found for user $username, using fixed 100%")
        }
        val availability = mostRecentAvailablilityValue.getOrElse(1.0)

        val result = schedules.toUserSchedules(username, availability)

        dataWriter ! result
      }

    case JiraIssueDetailsQueryTask(issueId: Either[String, Int]) =>
      val requester = sender()
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

              requester ! JiraIssueDetailsQueryTaskResponse(issue)
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
            case Xor.Left(error) =>
              log.error(error, "Could not decode Jira Tempo Team")
            case Xor.Right(jiraTempoTeams) =>
              dataWriter ! Teams(jiraTempoTeams.map(_.toTeam).toList)
              val teamIds = jiraTempoTeams.map(_.id).toList
              self ! JiraTempoTeamMembersQueryTask(teamIds)
              lastRead = Some(LocalDateTime.now())
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"JiraTempoTeamQueryTask task failed. Rescheduling in ${c.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(c.importWaitBetweenBatches, self, q)
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
            log.info(s"Scheduling next Teams import in ${c.importSyncInterval}")
            context.system.scheduler.scheduleOnce(c.importSyncInterval, self, JiraTempoTeamQueryTask)
            completedTeamsImportOnce = true
          } else {
            log.debug(s"Scheduling team query for next team in 3 second")
            context.system.scheduler.scheduleOnce(3.seconds, self, JiraTempoTeamMembersQueryTask(remainingTeamIds))
          }
        }),
        // Error handler
        ex => {
          log.error(ex, s"Team members query for team $teamId failed. Rescheduling im ${c.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(c.importWaitBetweenBatches, self, q)
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

  def retrieveUserAvailabilities(username: String, startDate: LocalDate, endDate: LocalDate) = {
    val resultPromise = Promise[JiraUserAvailabilities]

    val queryUri = getTempoUserAvailabilityUri(username, localDateEncoder.f(startDate), localDateEncoder.f(endDate))
    handleRequest(queryUri, signRequest = true,
      // Success handler
      jsonEntityHandler(_)(jsonString => {
        decode[List[JiraUserAvailability]](jsonString) match {
          case Xor.Left(e) =>
            log.error(e, s"Could not decode Tempo user availabilites")
            resultPromise.failure(e)
          case Xor.Right(x) =>
            val f = x.filterNot(e => filteredTeamIds.contains(e.teamId))
            resultPromise.success(JiraUserAvailabilities(f))
        }
      }),
      // Error handler
      e => {
        resultPromise.failure(e)
        log.error(e, s"TempoUserScheduleQueryTask task failed")
      })

    resultPromise.future
  }

  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val queryString = Query(Map(
      "dateFrom" -> fromDate.toString,
      "dateTo" -> toDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> c.tempoApiToken,
      "projectKey" -> c.timesheetProjectKey)).toString

    val path = Uri.Path(c.jiraTempoWorklogsServicePath)

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getJiraUsersRequestUri(mailSuffix: String) = {
    val path = Uri.Path(c.jiraUsersServicePath)
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
    val path = Uri.Path(c.jiraIssueDetailsServicePath) / issueIdString
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamsUri = {
    val path = Uri.Path(c.jiraTempoTeamServicePath)
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamMembersUri(teamId: Int) = {
    val path = Uri.Path(c.jiraTempoTeamMembersServicePath.format(teamId.toString))
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoUserScheduleUri(username: String, from: Date, to: Date) = {
    val path = Uri.Path(c.jiraTempoUserScheduleServicePath)
    val queryString = Query(Map(
      "user" -> username,
      "from" -> dateIsoFormatter(from),
      "to" -> dateIsoFormatter(to)
    )).toString()

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getTempoUserAvailabilityUri(username: String, from: Date, to: Date) = {
    val path = Uri.Path(c.jiraTempoUserAvailabilityServicePath.format(username))
    val queryString = Query(Map(
      "from" -> dateIsoFormatter(from),
      "to" -> dateIsoFormatter(to)
    )).toString()

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }
}

