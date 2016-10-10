package de.codecentric.ccdashboard.service.timesheet

import java.time.LocalDate
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.access.ReportAggregationResult
import de.codecentric.ccdashboard.service.timesheet.data.model._

/**
  * Collection of all message and query classes used in communication between the actors
  *
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
package object messages {

  /* Life-cycle messages */
  case class Start()

  case class Stop()

  /* Query messages */
  /**
    * Query for worklogs
    */
  case class WorklogQuery(username: String, from: Option[Date], to: Option[Date])

  /**
    * Query for worklogs response
    *
    * @param worklogs the worklogs returned by query
    */
  case class WorklogQueryResult(worklogs: Seq[Worklog])

  /**
    * Query for a user
    */
  case class UserQuery(username: String)

  /**
    * Query for user response
    */
  case class UserQueryResult(user: Option[User])

  /**
    * Query for an issue
    */
  case class IssueQuery(id: String)

  /**
    * Query for issue response
    */
  case class IssueQueryResult(issue: Option[Issue])

  /**
    * Query for all teams or one specific team
    */
  case class TeamQuery(teamId: Option[Int] = None)

  /**
    * Query in which team a user is and since when
    *
    * @param username
    */
  case class TeamMembershipQuery(username: String)

  case class TeamMembershipQueryResult(username: String, teamId: Int, teamName: String, dateFrom: Option[Date])

  /**
    * Query for all teams or one specific response
    */
  case class TeamQueryResponse(teams: Option[Teams])

  case class UserReportQuery(username: String, from: Option[Date], to: Option[Date], aggregationType: ReportQueryAggregationType.Value)

  case class TeamReportQuery(teamId: Int, from: Option[Date], to: Option[Date], aggregationType: ReportQueryAggregationType.Value)

  object ReportQueryAggregationType extends Enumeration {
    val DAILY = Value("daily")
    val MONTHLY = Value("monthly")
    val YEARLY = Value("yearly")
  }

  case class ReportEntry(billableHours: Option[Double] = None, adminHours: Option[Double] = None, vacationHours: Option[Double] = None,
                         preSalesHours: Option[Double] = None, recruitingHours: Option[Double] = None, illnessHours: Option[Double] = None,
                         travelTimeHours: Option[Double] = None, twentyPercentHours: Option[Double] = None, absenceHours: Option[Double] = None,
                         parentalLeaveHours: Option[Double] = None, otherHours: Option[Double] = None) {

    def +(o: ReportEntry) =
      ReportEntry.apply(
        add(billableHours, o.billableHours),
        add(adminHours, o.adminHours),
        add(vacationHours, o.vacationHours),
        add(preSalesHours, o.preSalesHours),
        add(recruitingHours, o.recruitingHours),
        add(illnessHours, o.illnessHours),
        add(travelTimeHours, o.travelTimeHours),
        add(twentyPercentHours, o.twentyPercentHours),
        add(absenceHours, o.absenceHours),
        add(parentalLeaveHours, o.parentalLeaveHours),
        add(otherHours, o.otherHours)
      )

    private def add(o1: Option[Double], o2: Option[Double]) = {
      (o1, o2) match {
        case (Some(v1), Some(v2)) => Some(v1 + v2)
        case (v@Some(v1), _) => v
        case (_, v@Some(v2)) => v
        case _ => None
      }
    }
  }

  case class ReportQueryResponse(dateFrom: Date, dateTo: Date, aggregation: String, result: ReportAggregationResult)

  case class Report(date: Date, report: ReportEntry)

  /* TODO: describe tasks */

  case class TempoWorklogQueryTask(toDate: LocalDate, fromDate: LocalDate, syncing: Boolean)

  case class TempoUserScheduleQueryTask(username: String, startDate: LocalDate, endDate: LocalDate)

  case class JiraUserQueryTask()

  case class JiraIssueDetailsQueryTask(issueId: Either[String, Int])

  case class JiraIssueDetailsQueryTaskResponse(issue: Issue)

  case class JiraTempoTeamQueryTask()

  case class JiraTempoTeamMembersQueryTask(teamIdsToQuery: List[Int])

  case class JiraTempoUserAvailabilityQueryTask(username: String, startDate: LocalDate, endDate: LocalDate)

  case class EnrichWorklogQueryData(username: String, worklogs: List[Worklog], issues: List[Issue])

}
