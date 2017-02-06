package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor._
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataWriterActor.TeamMemberships
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object DataProviderActor {
  /**
    * Query for worklogs
    */
  case class WorklogQuery(username: String, from: Option[Date], to: Option[Date])


  /**
    * Query for a user
    */
  case class UserQuery(username: String)

  /**
    * Query for an issue
    */
  case class IssueQuery(id: String)
  case class TeamQuery(teamId: Option[Int] = None)
  case class SingleTeamMembershipQuery(teamId: Int)

  case class TeamMemberQuery(teamId: Option[Int] = None)

  case object AllTeamMembershipQuery

  case class UserReportQuery(username: String, from: Option[Date], to: Option[Date], teamId: Option[Int], aggregationType: ReportQueryAggregationType.Value)

  case class TeamReportQuery(teamId: Int, from: Option[Date], to: Option[Date], aggregationType: ReportQueryAggregationType.Value)

  /**
    * Query for all employees
    */
  case object EmployeesQuery
}

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

      val resultFuture = for {
        (fromDate, toDate) <- getEmployeeSpecificDateRange(Option(startOfYear.asUtilDate), Option(endOfYear.asUtilDate),
          username, None)
        jiraReports <- dbReader.getUtilizationReport(username, fromDate, toDate)
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

    case UserReportQuery(username, from, to, teamId, aggregationType) =>
      val requester = sender()
      getUserReport(username, from, to, teamId, aggregationType).pipeTo(requester)

    case TeamReportQuery(teamId, from, to, aggregationType) =>
      val requester = sender()

      val rangeFromDate = from.getOrElse(localDateEncoder.f(importStartDate))
      val rangeToDate = to.getOrElse(new Date())

      dbReader.getTeamMembers(teamId).map(teamMembers => {

        val dateFilteredTeamMembers = filterTeamMembersInRange(rangeFromDate, rangeToDate, teamMembers)

        val usersReportsFut = Future.sequence(dateFilteredTeamMembers.map(member =>
          getUserReport(member.name, Some(rangeFromDate), Some(rangeToDate), Some(teamId), aggregationType)))

        val resultFut = for {
          allReportAggregations <- usersReportsFut.map(_.map(_.result))
        } yield {
          val allReports = allReportAggregations.flatMap(_.reports)
          //val size = allReportAggregationsList.size
          val overallHoursRequiredList = allReportAggregations.map(_.overallHoursRequired)
          val overallBillableHoursList = allReportAggregations.map(_.overallBillableHours)
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
          ReportQueryResponse(rangeFromDate, rangeToDate, aggregationType.toString, result)
        }

        resultFut.pipeTo(requester)
      }
      )
  }

  /**
    * Aggregates the report for a given user
    *
    * @param username        User to aggregate report for
    * @param from            Optional start date. If none provided, either the start date of the user in the team (if provided) is used. Otherwise the earliest available date in the database is used.
    * @param to              Optional end date. If none provided, either the end date fo the user in the team (if provided) is used. Otherwise today is used.
    * @param teamId          Optional team-id. If provided, only the time range of the user within the team is used.
    * @param aggregationType Aggregation type to perform
    * @return User report for given user
    */
  private def getUserReport(username: String, from: Option[Date],
                            to: Option[Date], teamId: Option[Int],
                            aggregationType: ReportQueryAggregationType.Value) = {
    for {
      (fromDate, toDate) <- getEmployeeSpecificDateRange(from, to, username, teamId)
      utilizationReports <- dbReader.getUtilizationReport(username, fromDate, toDate)
      workSchedule <- dbReader.getUserSchedule(username, fromDate, toDate)
    } yield {
      val reports = utilizationReports.map(
        u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours,
          u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours,
          u.otherHours)))
      val aggregationResult = aggregateReportsWithSchedules(reports, workSchedule, aggregationType)

      ReportQueryResponse(fromDate, toDate, aggregationType.toString, aggregationResult)
    }
  }

  /**
    * Filter all team members were active within the given selection range.
    * If their start- or end-dates lie within the range - they are active.
    * If they have no start-date but an end-date within or after the range - they are active.
    * If they have no end-date but an start-date within or before the range - they are active.
    * Otherwise they are not active.
    *
    * @param selectionRangeFromDate Start date of the range
    * @param selectionRangeToDate   End date of the range
    * @param teamMembers            Members to filter
    * @return Members that were active during the range
    */
  private def filterTeamMembersInRange(selectionRangeFromDate: Date, selectionRangeToDate: Date,
                                       teamMembers: List[TeamMember]) = {
    teamMembers.filter(member => {
      val fromCondition = member.dateFrom match {
        case None => true
        case Some(dateFrom) => !dateFrom.after(selectionRangeToDate)
      }

      val toCondition = member.dateTo match {
        case None => true
        case Some(dateTo) => !dateTo.before(selectionRangeFromDate)
      }

      fromCondition && toCondition
    })
  }

  /**
    * Calculates the employee specific from and end dates for the given user and team.
    *
    * @param from      Optional start date. If none provided, either the start date of the user in the team (if provided) is used. Otherwise the earliest available date in the database is used.
    * @param to        Optional end date. If none provided, either the end date fo the user in the team (if provided) is used. Otherwise today is used.
    * @param username  Username
    * @param teamIdOpt Optional team-id. If provided, only the time range of the user within the team is used.
    * @return Start and end dates for this employee (optional within the given team)
    */
  def getEmployeeSpecificDateRange(from: Option[Date], to: Option[Date], username: String,
                                   teamIdOpt: Option[Int]): Future[(Date, Date)] = {
    val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
    val toDate = to.getOrElse(new Date())

    dbReader.getUserTeamMembershipDates(username).map {
      userTeamMembershipDates => {
        val generalRange = (fromDate, toDate)

        if (userTeamMembershipDates.isEmpty || teamIdOpt.isEmpty) {
          generalRange
        } else {
          val teamId = teamIdOpt.get
          userTeamMembershipDates.find(_.teamId == teamId) match {
            case None => (fromDate, fromDate)
            case Some(member) =>
              // only select the more restrictive date from team-dates and selection-dates
              val teamStartDate = List(member.dateFrom, Some(fromDate)).flatten.max
              val teamEndDate = Seq(member.dateTo, Some(toDate)).flatten.min
              (teamStartDate, teamEndDate)
          }
        }
      }
    }
  }

  def aggregateReportsWithSchedules(reports: List[(Date, ReportEntry)], workSchedule: List[UserSchedule],
                                    aggregationType: ReportQueryAggregationType.Value): ReportAggregationResult = {
    val aggregator = ReportAggregator(reports, workSchedule)

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


}