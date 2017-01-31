package de.codecentric.ccdashboard.service.timesheet.data.ingest.jira

import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.time.{LocalDate, LocalDateTime}

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.util.Timeout
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

class JiraDataReaderActor(conf: Config, dataWriter: ActorRef) extends BaseDataReaderActor(dataWriter) with JiraRequestUriGenerators {
  val databaseHasBeenInitialized: Boolean = conf.getBoolean("timesheet-service.initializeDatabase")

  val jiraConf: Config = ConfigFactory.load("jiraclient.conf").getConfig("jira")

  override def getOAuthConfig = Some(jiraConf)

  override def scheme = jiraConf.getString("scheme")

  override def host = jiraConf.getString("host")

  override def authority = Uri.Authority(Uri.Host(host))

  // Team-IDs to filter. 4 = codecentric ALL
  val filteredTeamIds = Set(4)

  val aggregationActor: ActorRef = context.actorOf(Props(new DataAggregationActor(conf, dataWriter)))

  val jiraConfig = new JiraConfig(jiraConf, conf)

  // A few indicators or counters
  var completedUsersImportOnce: Boolean = !databaseHasBeenInitialized
  var completedTeamsImportOnce: Boolean = !databaseHasBeenInitialized
  var completedWorklogsImportOnce: Boolean = !databaseHasBeenInitialized
  var lastRead: Option[LocalDateTime] = None

  import context.dispatcher

  log.debug(s"Instantiated with: $scheme, $host, ${jiraConfig.jiraTempoWorklogsServicePath}, ${jiraConfig.accessToken}, ${jiraConfig.tempoApiToken}, ${jiraConfig.consumerPrivateKey}, ${jiraConfig.importStartDate}, ${jiraConfig.importEndDate}")

  def receive = {
    /**
      * Perform startup and send initial request
      */
    case Start =>
      log.info("Received Start message -> commencing to query Jira")
      // Start Tempo Worklog Query async
      val now = LocalDate.now()
      context.system.scheduler.scheduleOnce(0.seconds, self,
        TempoWorklogQueryTask(now, now.minusDays(jiraConfig.importBatchSizeDays), syncing = !databaseHasBeenInitialized))

      // Start Jira User Queries async
      context.system.scheduler.scheduleOnce(1.seconds, self, JiraUserQueryTask)

      // Query tempo teams
      context.system.scheduler.scheduleOnce(2.seconds, self, JiraTempoTeamQueryTask)

    /**
      * Handle the different query messages
      */
    case q@TempoWorklogQueryTask(toDate, fromDate, syncing) =>
      implicit val timeout = Timeout(60.seconds)

      log.debug("Tempo query task received.")

      retrieveTempoWorklogs(fromDate, toDate).onComplete {
        case Success(v) =>
          val worklogs = Worklogs(v.map(_.toWorklog))

          dataWriter ! worklogs

          val issueResponses = worklogs.content
            .map(_.issueKey).distinct
            .map(issueKey => (self ? JiraIssueDetailsQueryTask(Left[String, Int](issueKey))).mapTo[JiraIssueDetailsQueryTaskResponse])

          val issueResponseList = Future.sequence(issueResponses)
            .map(_.map(_.issue))

          val userWorklogGroups = worklogs.content.groupBy(_.username)

          val enrichmentRequests = for {
            issues <- issueResponseList
          } yield {
            userWorklogGroups.map {
              case (username, worklogList) =>
                val worklogIssues = worklogList.map(_.issueKey).distinct
                val relevantIssues = issues.filter(i => worklogIssues.contains(i.issueKey))
                (username, worklogList, relevantIssues)
            }
          }

          enrichmentRequests.map(_.foreach(r => aggregationActor ! PerformUtilizationAggregation(r._1, r._2, r._3)))

          // Determine which task to query when
          val lastDayOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
          val daysUntilEndOfYear = ChronoUnit.DAYS.between(LocalDate.now(), lastDayOfYear)
          val syncRangeDays = jiraConfig.importSyncRangeDays + daysUntilEndOfYear

          val nextImport = if (syncing) {
            if (fromDate.isBefore(lastDayOfYear.minusDays(syncRangeDays))) {
              TempoWorklogQueryTask(lastDayOfYear, lastDayOfYear.minusDays(jiraConfig.importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(jiraConfig.importBatchSizeDays), syncing = true)
            }
          } else {
            if (fromDate.isBefore(jiraConfig.importStartDate)) {
              completedWorklogsImportOnce = true
              TempoWorklogQueryTask(lastDayOfYear, lastDayOfYear.minusDays(jiraConfig.importBatchSizeDays), syncing = true)
            } else {
              TempoWorklogQueryTask(fromDate, fromDate.minusDays(jiraConfig.importBatchSizeDays), syncing = false)
            }
          }
          lastRead = Some(LocalDateTime.now())
          context.system.scheduler.scheduleOnce(jiraConfig.importWaitBetweenBatches, self, nextImport)

        case Failure(e) =>
          log.error(e, s"TempoWorklogQueryTask task failed. Rescheduling in ${jiraConfig.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(jiraConfig.importWaitBetweenBatches, self, q)
      }

    case q@JiraUserQueryTask =>
      log.debug("Jira user query task received.")

      val ccUsersPromise = Promise[List[User]]
      val instanaUsersPromise = Promise[List[User]]

      Seq(
        (ccUsersPromise, "codecentric.de"),
        (instanaUsersPromise, "instana.com")
      ).foreach {
        case (promise, mailSuffix) =>
          val uri = getJiraUsersRequestUri(mailSuffix)

          val future = performAsyncJsonQuery(uri, s => decode[List[JiraUser]](s), "Could not decode users.")
            .map(_.map(_.toUser))

          promise.completeWith(future)
      }

      val allUsers = for {
        ccUsers <- ccUsersPromise.future
        instanaUsers <- instanaUsersPromise.future
      } yield Users(ccUsers ++ instanaUsers)

      val lastDayOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      // tribger update of user schedule queries
      allUsers
        .map(_.content.map(_.name))
        .map(_.map(username => TempoUserScheduleQueryTask(username, jiraConfig.importStartDate, lastDayOfYear)))
        .map(_.foreach(task => self ! task))

      allUsers.onComplete {
        case Success(users) =>
          dataWriter ! users

          lastRead = Some(LocalDateTime.now())
          completedUsersImportOnce = true

          log.info(s"Scheduling next users-import in ${jiraConfig.importSyncInterval}")
          context.system.scheduler.scheduleOnce(jiraConfig.importSyncInterval, self, q)

        case Failure(ex) =>
          log.error(ex, s"Users-import failed. Rescheduling next users-import in ${jiraConfig.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(jiraConfig.importWaitBetweenBatches, self, q)
      }

    case TempoUserScheduleQueryTask(username, startDate, endDate) =>
      log.debug("Jira user schedules task received.")

      val jiraUserAvailabilitiesFuture = retrieveUserAvailabilities(username, startDate, endDate)

      val jiraUserSchedulesFuture = retrieveUserSchedules(username, startDate, endDate)

      for {
        schedules <- jiraUserSchedulesFuture
        availabilites <- jiraUserAvailabilitiesFuture
      } yield {
        val mostRecentAvailablilityValue = availabilites
          .toUserAvailabilities
          .content
          .sortWith((l, r) => l.dateFrom.isAfter(r.dateFrom))
          .headOption
          .map(_.availability)

        if (mostRecentAvailablilityValue.isEmpty) {
          log.debug(s"No availability value found for user $username, using fixed 100%")
        }
        val availability = mostRecentAvailablilityValue.getOrElse(1.0)

        val result = schedules.toUserSchedules(username, availability)

        dataWriter ! result
      }

    case JiraIssueDetailsQueryTask(issueId: Either[String, Int]) =>
      val requester = sender()
      log.debug(s"Querying issue details task for issue id $issueId received.")

      retrieveJiraIssueDetails(issueId).map(_.toIssue).onComplete {
        case Success(v) =>
          dataWriter ! v
          lastRead = Some(LocalDateTime.now())
          requester ! JiraIssueDetailsQueryTaskResponse(v)

        case Failure(_) =>
      }

    case q@JiraTempoTeamQueryTask =>
      log.debug("Jira Tempo Team task received.")

      retrieveJiraTempoTeams().onComplete {
        case Success(v) =>
          dataWriter ! Teams(v.map(_.toTeam).toList)
          val teamIds = v.map(_.id).toList
          self ! JiraTempoTeamMembersQueryTask(teamIds)
          lastRead = Some(LocalDateTime.now())

        case Failure(e) =>
          log.error(e, s"JiraTempoTeamQueryTask task failed. Rescheduling in ${jiraConfig.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(jiraConfig.importWaitBetweenBatches, self, q)
      }

    case q@JiraTempoTeamMembersQueryTask((teamId :: remainingTeamIds)) =>
      log.debug("Jira Tempo Team Members task received.")

      retrieveTempoTeamMembers(teamId).onComplete {
        case Success(v) =>
          // Inject teamId after parsing since response does not contain it
          val teamMembers = v.map(_.toTeamMember.copy(teamId = teamId)).toList
          dataWriter ! TeamMemberships(teamId, teamMembers)

          if (remainingTeamIds.isEmpty) {
            log.info(s"Scheduling next Teams import in ${jiraConfig.importSyncInterval}")
            context.system.scheduler.scheduleOnce(jiraConfig.importSyncInterval, self, JiraTempoTeamQueryTask)
            completedTeamsImportOnce = true
          } else {
            log.debug(s"Scheduling team query for next team in 3 second")
            context.system.scheduler.scheduleOnce(3.seconds, self, JiraTempoTeamMembersQueryTask(remainingTeamIds))
          }

          lastRead = Some(LocalDateTime.now())

        case Failure(e) =>
          log.error(e, s"Team members query for team $teamId failed. Rescheduling im ${jiraConfig.importWaitBetweenBatches}")
          context.system.scheduler.scheduleOnce(jiraConfig.importWaitBetweenBatches, self, q)
      }

    case StatusRequest(statusActor) =>
      val importStatusList = List(completedUsersImportOnce, completedTeamsImportOnce, completedWorklogsImportOnce)

      val List(usersImportStatus, teamsImportStatus, worklogsImportStatus) =
        importStatusList.map(f => if (f) "syncing" else "importing")

      statusActor ! StatusNotification("JiraDataReader", Map(
        "users import status" -> usersImportStatus,
        "teams import status" -> teamsImportStatus,
        "worklogs import status" -> worklogsImportStatus,
        "last read" -> lastRead.map(_.toString).getOrElse("")),
        Some(importStatusList.forall(identity))
      )
  }

  def retrieveUserAvailabilities(username: String, startDate: LocalDate, endDate: LocalDate) = {
    val queryUri = getTempoUserAvailabilityUri(username, localDateEncoder.f(startDate), localDateEncoder.f(endDate))

    val resultFuture = performAsyncJsonQuery[List[JiraUserAvailability]](queryUri, s => decode[List[JiraUserAvailability]](s), "Could not decode Tempo user availabilites")

    resultFuture.map(JiraUserAvailabilities)
  }

  def retrieveUserSchedules(username: String, startDate: LocalDate, endDate: LocalDate) = {
    val queryUri = getJiraTempoUserScheduleUri(username, localDateEncoder.f(startDate), localDateEncoder.f(endDate))

    performAsyncJsonQuery(queryUri, decode[JiraUserSchedules], s"Could not decode Jira user schedule for user $username")
  }

  def retrieveJiraIssueDetails(issueId: Either[String, Int]) = {
    val queryUri = getJiraIssueDetailsUri(issueId)

    performAsyncJsonQuery(queryUri, decode[JiraIssue], s"Could not decode issue $issueId")
  }

  def retrieveJiraTempoTeams() = {
    val queryUri = getJiraTempoTeamsUri

    performAsyncJsonQuery(queryUri, decode[Seq[JiraTempoTeam]], s"Could not retrieve Tempo Teams")
  }

  def retrieveTempoTeamMembers(teamId: Int) = {
    val queryUri = getJiraTempoTeamMembersUri(teamId)

    performAsyncJsonQuery(queryUri, decode[Seq[JiraTempoTeamMember]], s"Could not retrieve Tempo Team Members")
  }

  def retrieveTempoWorklogs(fromDate: LocalDate, toDate: LocalDate) = {
    val queryUri = getWorklogRequestUri(fromDate, toDate)

    implicit val um = jiraWorklogUnmarshaller
    implicit val timeout = Timeout(60.seconds)

    performAsyncHttpEntityQuery[List[JiraWorklog]](queryUri, entity => Unmarshal(entity).to[List[JiraWorklog]], "TempoWorklogQueryTask task failed.")
  }

}

