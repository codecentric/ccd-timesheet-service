package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{Clock, LocalDate}
import java.time.LocalDate.now
import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{UserSchedule, UserUtilization}
import de.codecentric.ccdashboard.service.timesheet.messages.WorkScheduleEntry
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._
/**
  * Created by tbinias on 23.12.16.
  */
class WorkScheduleService(fullYearSchedules: List[UserSchedule], fullYearReports: List[UserUtilization],
                          employeeSince: Option[Date], year: Int, providedClock: Option[Clock] = None) {

  implicit final val clock: Clock = providedClock.getOrElse(Clock.systemDefaultZone())

  final private val TARGET_HOURS_BASE = 1440
  final private val VACATION_DAYS_PER_YEAR = 30

  final private val startOfYear = LocalDate.ofYearDay(year, 1)
  final private val endOfYear = startOfYear.`with`(TemporalAdjusters.lastDayOfYear())

  final val userStartOfYear: Date = employeeSince.getOrElse(startOfYear.asUtilDate(clock))
  val userStartThisYear: Date = Seq(startOfYear.asUtilDate(clock), userStartOfYear).max
  private val userMonthsThisYear = ChronoUnit.MONTHS.between(userStartThisYear.asLocalDate(clock), endOfYear) +
    (if (userStartThisYear.asLocalDate(clock).getDayOfMonth == 15) 0.5 else 1)
  final val vacationDaysThisYear: Long = (userMonthsThisYear * VACATION_DAYS_PER_YEAR / 12).round

  final val workDaysThisYear: Int = fullYearSchedules.count(_.requiredHours > 0)
  private val userSchedules = fullYearSchedules.filter(!_.workDate.before(userStartThisYear))
  final val userWorkDaysThisYear: Double = getWorkDaysFromUserSchedules(userSchedules)
  final val userWorkDaysAvailabilityRate: Double = userWorkDaysThisYear / workDaysThisYear

  final val parentalLeaveDaysThisYear: Double = fullYearReports.flatMap(_.parentalLeaveHours).sum / 8
  final val targetHoursThisYear: Double = (TARGET_HOURS_BASE * userWorkDaysAvailabilityRate) - (parentalLeaveDaysThisYear * 8 * 0.8)

  final val burndownHoursPerWorkday: Double = targetHoursThisYear / (userWorkDaysThisYear - vacationDaysThisYear - parentalLeaveDaysThisYear)

  def getWorkScheduleUntil(endDate: Date): WorkScheduleEntry = {
    val reportsTillEndDate = fullYearReports.filter(_.day.before(endDate))

    val usedVacationDaysTillTodayInclusive = reportsTillEndDate.flatMap(_.vacationHours).sum / 8
    val personalLimitedVacationDays = Seq(usedVacationDaysTillTodayInclusive, vacationDaysThisYear.toDouble).min

    val usedParentalLeaveDaysTillTodayInclusive = reportsTillEndDate.flatMap(_.parentalLeaveHours).sum / 8

    val remainingVacationDaysThisYear = vacationDaysThisYear - personalLimitedVacationDays
    val vacationDaysUsageEstimation = getVacationDaysUsageEstimation(remainingVacationDaysThisYear, endDate)

    val workDaysTillEndDate = getWorkDaysFromUserSchedules(userSchedules.filter(_.workDate.before(endDate)))
    val remainingWorkDays = workDaysTillEndDate - personalLimitedVacationDays - usedParentalLeaveDaysTillTodayInclusive

    val targetDays = remainingWorkDays - vacationDaysUsageEstimation

    val targetHoursToday = List(targetDays * burndownHoursPerWorkday, targetHoursThisYear).min

    WorkScheduleEntry(endDate,
      usedVacationDaysTillTodayInclusive,
      usedParentalLeaveDaysTillTodayInclusive,
      targetHoursToday)
  }

  /**
    * When we're looking at the past, we know the vacation usage for sure so we don't have to adjust.
    * When we're looking at the future however, we have to guess the average usage of vacation days per month
    *
    * @return Estimated number of vacation days to take each month
    */
  private def getVacationDaysUsageEstimation(remainingVacationDaysThisYear: Double, endDate: Date) = {
    val selectedMonth = endDate.asLocalDate(clock).getMonthValue
    val selectedYear = endDate.asLocalDate(clock).getYear
    val remainingMonths = List(12 - selectedMonth + 1, 1).max

    if (selectedMonth <= now(clock).getMonthValue || selectedYear < now(clock).getYear) {
      0.0
    } else {
      remainingVacationDaysThisYear / remainingMonths
    }
  }

  private def getWorkDaysFromUserSchedules(schedules: List[UserSchedule]) = {
    schedules.map(_.requiredHours).sum / 8
  }

}
