package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{LocalDate, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.UserSchedule
import de.codecentric.ccdashboard.service.timesheet.messages.ReportEntry

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

class ReportAggregator(reports: List[(Date, ReportEntry)], workSchedule: List[UserSchedule]) {

  import ReportAggregator._

  val today: Date = Date.from(LocalDate.now().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)
  val tomorrow: Date = Date.from(LocalDate.now().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant)

  val overallHoursRequired: Double = workSchedule.map(_.requiredHours).sum
  val overallBillableHours: Double = reports.flatMap(_._2.billableHours).sum
  val overallUtilization: Double = utilization(overallHoursRequired, overallBillableHours)
  val daysWithoutBookedHours: List[Date] = getDaysWithoutBookedHours

  def aggregateDaily(teamId: Option[Int]): ReportAggregationResult = aggregate(dayFormatter, teamId)

  def aggregateMonthly(teamId: Option[Int]): ReportAggregationResult = aggregate(monthFormatter, teamId)

  def aggregateYearly(teamId: Option[Int]): ReportAggregationResult = aggregate(yearFormatter, teamId)

  /**
    * Utilization calculation function
    *
    * @param hoursToWork   Hours that this user should have worked
    * @param billableHours Hours that were billable
    * @return Utilization value
    */
  private def utilization(hoursToWork: Double, billableHours: Double) = {
    if (hoursToWork < 1) billableHours else billableHours / hoursToWork
  }

  private def getDaysWithoutBookedHours = {
    val datesRequireBooking = workSchedule.filter(_.requiredHours > 0).map(_.workDate)

    val datesWithBooking = reports.map(_._1)
    datesRequireBooking.filterNot(date => datesWithBooking.contains(date))
                       .filterNot(date => date.equals(today))
                       .sorted

  }

  private def aggregate(formatter: DateTimeFormatter, teamId: Option[Int]) = {
    val workScheduleGroup = workSchedule.groupBy(s => formatter.format(localDateDecoder.f(s.workDate)))

    val requiredHoursByKey = workScheduleGroup
      .mapValues(_.foldLeft(0.0)((sum, elem) => sum + elem.requiredHours))

    val hoursReportGroup = reports
      .map({ case (date, elem) => formatter.format(localDateDecoder.f(date)) -> elem })
      .groupBy(_._1)
      .mapValues(_.map(_._2))

    val hoursReportByKey = hoursReportGroup
      .mapValues(_.reduce((left, right) => left + right))

    val valuesByKey = requiredHoursByKey.map({ case (id, requiredHours) =>
      val report = hoursReportByKey.getOrElse(id, ReportEntry())
      val utilizationValue = utilization(requiredHours, report.billableHours.getOrElse(0.0))
      // Note: Other indicators may be inserted here
      id -> (report, utilizationValue)
    })

    val sortedValuesByKey = valuesByKey.toList.sortBy(_._1)

    val reportsList = sortedValuesByKey.map({ case (key, (report, utilization)) => ReportAggregation(key, report, utilization) })

    ReportAggregationResult(overallHoursRequired, overallBillableHours, overallUtilization, teamId, daysWithoutBookedHours, reportsList)
  }
}

object ReportAggregator {
  def apply(reports: List[(Date, ReportEntry)], workSchedule: List[UserSchedule]): ReportAggregator =
    new ReportAggregator(reports, workSchedule)

  private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
  private val yearFormatter = DateTimeFormatter.ofPattern("yyyy")
}

case class ReportAggregation(key: String, report: ReportEntry, utilization: Double, numberOfConsultants: Int = 1)

case class ReportAggregationResult(overallHoursRequired: Double,
                                   overallBillableHours: Double,
                                   overallUtilization: Double,
                                   team: Option[Int],
                                   daysWithoutBookedHours: List[Date],
                                   reports: List[ReportAggregation])
