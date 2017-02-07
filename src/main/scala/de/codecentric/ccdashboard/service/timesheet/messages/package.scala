package de.codecentric.ccdashboard.service.timesheet

import java.time.LocalDate
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.access.ReportAggregationResult
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataWriterActor.TeamMemberships
import de.codecentric.ccdashboard.service.timesheet.data.model._

/**
  * Collection of all message and query classes used in communication between the actors
  *
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
package object messages {

  /* Query messages */

  /**
    * Query for worklogs response
    *
    * @param worklogs the worklogs returned by query
    */
  case class WorklogQueryResult(worklogs: Seq[Worklog])


  case class VacationHours(used: Double, planned: Double, free: Double)

  /**
    * Query for user response
    */
  case class UserQueryResult(userkey: Option[String] = None,
                             name: Option[String] = None,
                             emailAddress: Option[String] = None,
                             avatarUrl: Option[String] = None,
                             displayName: Option[String] = None,
                             active: Option[Boolean] = None,
                             vacationHours: Option[VacationHours] = None)


  /**
    * Query for issue response
    */
  case class IssueQueryResult(issue: Option[Issue])
  /**
    * Query in which team a user is and since when
    */

  case class TeamMembershipQueryResult(username: String, teamId: Int, teamName: String, dateFrom: Option[Date])

  case class SingleTeamMembershipQueryResponse(team: Option[TeamMemberships])

  case class AllTeamMembershipQueryResponse(teams: List[SingleTeamMembershipQueryResponse])



  case class EmployeesQueryResponse(employees: List[String])

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
        case (v@Some(_), _) => v
        case (_, v@Some(_)) => v
        case _ => None
      }
    }
  }

  case class ReportQueryResponse(dateFrom: Date, dateTo: Date, aggregation: String, result: ReportAggregationResult)

  case class Report(date: Date, report: ReportEntry)

  /* TODO: describe tasks */

  case class EnrichWorklogQueryData(username: String, worklogs: List[Worklog], issues: List[Issue])


  case class WorkScheduleEntry(date: Date,
                               usedVacationDays: Double,
                               usedParentalLeaveDays: Double,
                               targetHours: Double)

  case class WorkScheduleQueryResult(username: String,
                                     userStartOfYear: Date,
                                     workDaysThisYear: Long,
                                     userWorkDaysThisYear: Long,
                                     userWorkDaysAvailabilityRate: Double,
                                     vacationDaysThisYear: Long,
                                     usedVacationDays: Double,
                                     plannedVacationDays: Double,
                                     parentalLeaveDaysThisYear: Double,
                                     targetHoursThisYear: Double,
                                     burndownHoursPerWorkday: Double,
                                     totalWorkSchedule: WorkScheduleEntry,
                                     monthlyAccumulation: List[WorkScheduleEntry])

}
