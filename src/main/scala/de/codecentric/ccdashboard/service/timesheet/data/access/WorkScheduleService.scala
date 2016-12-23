package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{UserSchedule, UserUtilization}
import de.codecentric.ccdashboard.service.timesheet.messages.{WorkScheduleEntry}

/**
  * Created by tbinias on 23.12.16.
  */
class WorkScheduleService(fullYearSchedules: List[UserSchedule], fullYearReports: List[UserUtilization], employeeSince: Option[Date]) {

  final private val TARGET_HOURS_BASE = 1440
  final private val VACATION_DAYS_PER_YEAR = 30

  final private val startOfYear = LocalDate.now().withDayOfYear(1)
  final private val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())

  final val userStartOfYear = employeeSince.getOrElse(asUtilDate(startOfYear))
  private val userMonthsThisYear = ChronoUnit.MONTHS.between(asLocalDate(userStartOfYear), endOfYear) +
    (if (asLocalDate(userStartOfYear).getDayOfMonth == 15) 0.5 else 1)
  final val vacationDaysThisYear = (userMonthsThisYear * VACATION_DAYS_PER_YEAR / 12).round

  final val workDaysThisYear = fullYearSchedules.filter(_.requiredHours > 0).size
  private val userSchedules = fullYearSchedules.filter(s => s.workDate.after(userStartOfYear) || s.workDate.equals(userStartOfYear))
  final val userWorkDaysThisYear = getWorkDaysFromUserSchedules(userSchedules)
  final val userWorkDaysAvailabilityRate = userWorkDaysThisYear / workDaysThisYear

  final val parentalLeaveDaysThisYear = fullYearReports.flatMap(_.parentalLeaveHours).sum / 8
  final val targetHoursThisYear = (TARGET_HOURS_BASE * userWorkDaysAvailabilityRate) - (parentalLeaveDaysThisYear * 8 * 0.8)

  final val burndownHoursPerWorkday = targetHoursThisYear / (userWorkDaysThisYear - vacationDaysThisYear - parentalLeaveDaysThisYear)


  def getWorkScheduleUntil(endDate: Date): WorkScheduleEntry = {
    val reportsTillEndDate = fullYearReports.filter(_.day.before(endDate))
    val usedVacationDaysTillTodayInclusive = reportsTillEndDate.flatMap(_.vacationHours).sum / 8
    val usedParentalLeaveDaysTillTodayInclusive = reportsTillEndDate.flatMap(_.parentalLeaveHours).sum / 8

    val workDaysTillEndDate = getWorkDaysFromUserSchedules(userSchedules.filter(_.workDate.before(endDate)))
    val targetHoursToday =
      (workDaysTillEndDate - usedVacationDaysTillTodayInclusive - usedParentalLeaveDaysTillTodayInclusive) * burndownHoursPerWorkday

    WorkScheduleEntry(endDate,
      usedVacationDaysTillTodayInclusive,
      usedParentalLeaveDaysTillTodayInclusive,
      targetHoursToday)
  }

  private def getWorkDaysFromUserSchedules(schedules: List[UserSchedule]) = {
    schedules.map(_.requiredHours).sum / 8
  }

  private def asUtilDate(localDate :LocalDate): Date = {
    Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())
  }

  private def asUtilDate(localDateTime :LocalDateTime): Date = {
    Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())
  }

  private def asLocalDate(date: Date): LocalDate = {
    date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
  }

}
