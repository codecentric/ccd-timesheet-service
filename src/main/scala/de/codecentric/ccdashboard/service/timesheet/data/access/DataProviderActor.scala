package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(startDate: => LocalDate, dbReader: DatabaseReader) extends Actor with ActorLogging {

  val importStartDate: LocalDate = startDate

  private var userQueryCount = 0L
  private var teamMembershipQueryCount = 0L
  private var worklogQueryCount = 0L
  private var issueQueryCount = 0L

  import context.dispatcher

  def getEmployeeSpecificDateRange(from: Option[Date], to: Option[Date], username: String): (Future[Date], Date) = {
    val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
    val toDate = to.getOrElse(new Date())

    val employeeSinceDateFuture = dbReader.getTeamMembershipStartDates(username)

    val dateToUse = for {
      employeeSinceDateList <- employeeSinceDateFuture
    } yield {
      employeeSinceDateList match {
        case Nil => fromDate
        case list => List(list.min, fromDate).max
      }
    }
    (dateToUse, toDate)
  }

  def aggregateReportsWithSchedules(reports: List[(Date, ReportEntry)], workSchedule: List[UserSchedule],
                                    aggregationType: ReportQueryAggregationType.Value): ReportAggregationResult = {
    val aggregator = ReportAggregator(reports, workSchedule)
    // Aggregate Reports
    aggregationType match {
      case ReportQueryAggregationType.DAILY =>
        aggregator.aggregateDaily()

      case ReportQueryAggregationType.MONTHLY =>
        aggregator.aggregateMonthly()

      case ReportQueryAggregationType.YEARLY =>
        aggregator.aggregateYearly()
    }
  }


  def generateReportQueryResponse(fromDate: Date, toDate: Date,
                                  aggregationResultFuture: Future[ReportAggregationResult],
                                  aggregationType: ReportQueryAggregationType.Value): Future[ReportQueryResponse] =
    aggregationResultFuture.map { aggregationResult => {
      ReportQueryResponse(fromDate, toDate, aggregationType.toString, aggregationResult)
    }
    }

  private def getVacationHours(reports: List[(Date, ReportEntry)]) = {
    val today = LocalDate.now().asUtilDate
    val tomorrow = LocalDate.now().plusDays(1).asUtilDate
    val usedHours = reports.filter(_._1.before(tomorrow)).flatMap(_._2.vacationHours).sum
    val plannedHours = reports.filter(_._1.after(today)).flatMap(_._2.vacationHours).sum
    VacationHours(usedHours, plannedHours, 30 * 8 - usedHours - plannedHours)
  }

  def receive: Receive = {
    case WorklogQuery(username, from, to) =>
      val requester = sender()
      log.debug("Received WorklogQuery")
      dbReader.getWorklog(username, from, to)
        .pipeTo(requester)
      worklogQueryCount = worklogQueryCount + 1

    case UserQuery(username) =>
      val requester = sender()
      log.debug("Received UserQuery")

      val startOfYear = LocalDate.now().withDayOfYear(1)
      val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      val (fromDate, toDate) = getEmployeeSpecificDateRange(Option(startOfYear.asUtilDate),
        Option(endOfYear.asUtilDate), username)

      val resultFuture = for {
        date <- fromDate
        jiraReports <- dbReader.getUtilizationReport(username, date, toDate)
        userOption <- dbReader.getUserByName(username)
      } yield {
        val reports = jiraReports.map(
          u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours,
            u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours,
            u.otherHours)))

        userOption.map(u =>
          UserQueryResult(Option(u.userkey), Option(u.name), Option(u.emailAddress), Option(u.avatarUrl),
            Option(u.displayName), Option(u.active), Option(getVacationHours(reports)))
        ).getOrElse(UserQueryResult())

      }

      resultFuture.pipeTo(requester)
      userQueryCount = userQueryCount + 1


    case IssueQuery(id) =>
      val requester = sender()
      log.debug("Received IssueQuery")
      val result = dbReader.getIssueById(id)

      result.onComplete {
        case Success(issue) => requester ! IssueQueryResult(Some(issue))
        case Failure(_) => requester ! IssueQueryResult(None)
      }
      issueQueryCount = issueQueryCount + 1

    case SingleTeamMembershipQuery(teamId) =>
      val requester = sender()
      teamMembershipQueryCount = teamMembershipQueryCount + 1
      dbReader.getTeamMembers(teamId)
        .map(members => SingleTeamMembershipQueryResponse(Some(TeamMemberships(teamId, members))))
        .pipeTo(requester)


    case AllTeamMembershipQuery =>
      val requester = sender()
      teamMembershipQueryCount = teamMembershipQueryCount + 1

      dbReader.getTeamIds().flatMap(teamIds => {
        Future.sequence(
          teamIds.map(teamId => {
            dbReader.getTeamMembers(teamId)
              .map(members => SingleTeamMembershipQueryResponse(Some(TeamMemberships(teamId, members))))
          }))
      }).map(AllTeamMembershipQueryResponse)
        .pipeTo(requester)

    case EmployeesQuery =>
      val requester = sender()
      val query = dbReader.getEmployees()
      query.pipeTo(requester)

    case UserReportQuery(username, from, to, aggregationType) =>
      val requester = sender()

      val (fromDate, toDate) = getEmployeeSpecificDateRange(from, to, username)

      val resultFut = for {
        date <- fromDate
        utilizationReports <- dbReader.getUtilizationReport(username, date, toDate)
        workSchedule <- dbReader.getUserSchedule(username, date, toDate)
      } yield {
        val reports = utilizationReports.map(
          u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours,
            u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours,
            u.otherHours)))
        val aggregationResult = aggregateReportsWithSchedules(reports, workSchedule, aggregationType)

        ReportQueryResponse(date, toDate, aggregationType.toString, aggregationResult)
      }

      resultFut.pipeTo(requester)

    case TeamReportQuery(teamId, from, to, aggregationType) =>
      val requester = sender()

      val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
      val toDate = to.getOrElse(new Date())

      // get all members of the team
      val usernamesFut = dbReader.getTeamMembers(teamId).map(memberList => memberList.map(_.name))

      usernamesFut.map(usernames => {
        implicit val timeout = Timeout(60.seconds)

        val usersReportsFut = Future.sequence(usernames.map(username => {
          (self ? UserReportQuery(username, from, to, aggregationType)).mapTo[ReportQueryResponse]
        }))

        val resultFut = for {
          allReportAggregations <- usersReportsFut.map(_.map(_.result))
        } yield {
          val allReportAggregationsList = allReportAggregations.toList
          val allReports = allReportAggregationsList.flatMap(_.reports)
          //val size = allReportAggregationsList.size
          val overallHoursRequiredList = allReportAggregationsList.map(_.overallHoursRequired)
          val overallBillableHoursList = allReportAggregationsList.map(_.overallBillableHours)
          //val overallUtilizationList = allReportAggregationsList.map(_.overallUtilization)
          val keyGroupedReports = allReports.groupBy(_.key)

          val teamReportAggregation = keyGroupedReports.map {
            case (key, reportAggregations) =>
              val reports = reportAggregations.map(_.report)
              val utilization = reportAggregations.map(_.utilization)
              val numberOfConsultants = reports.size

              val reducedReports = reports.reduce((l, r) => l + r)
              val reducedUtilization = utilization.sum / utilization.size

              ReportAggregation(key, reducedReports, reducedUtilization, numberOfConsultants)
          }

          val overallHoursRequired = overallHoursRequiredList.sum
          val overallBillableHours = overallBillableHoursList.sum
          val overallUtilization = teamReportAggregation.map(_.utilization).sum / teamReportAggregation.size
          val result = ReportAggregationResult(overallHoursRequired, overallBillableHours, overallUtilization,
            List[Date](), teamReportAggregation.toList.sortBy(_.key))
          ReportQueryResponse(fromDate, toDate, aggregationType.toString, result)
        }

        resultFut.pipeTo(requester)
      }
      )
  }
}