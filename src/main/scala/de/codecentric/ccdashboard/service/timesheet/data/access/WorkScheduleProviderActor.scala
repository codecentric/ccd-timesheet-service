package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, LocalDateTime}
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import de.codecentric.ccdashboard.service.timesheet.data.access.WorkScheduleProviderActor.WorkScheduleQuery
import de.codecentric.ccdashboard.service.timesheet.data.model.UserSchedule
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._

import scala.concurrent.Future


object WorkScheduleProviderActor {
  case class WorkScheduleQuery(username: String, year: Option[Int])
}

/**
  * Created by tbinias on 22.12.16.
  */
class WorkScheduleProviderActor(dbReader: DatabaseReader) extends Actor with ActorLogging {

  import context.dispatcher

  def receive: Receive = {
    case WorkScheduleQuery(username: String, year: Option[Int]) =>
      val requester = sender()
      log.debug("Received WorkScheduleQuery")
      val startOfYear = LocalDate.ofYearDay(year.getOrElse(LocalDate.now().getYear), 1)
      val endOfYear = startOfYear.`with`(TemporalAdjusters.lastDayOfYear())
      val resultFuture = for {
         fullYearSchedules <- dbReader.getUserSchedules(username, startOfYear.asUtilDate, endOfYear.asUtilDate)
         fullYearReports <- dbReader.getUtilizationReport(username, startOfYear.asUtilDate, endOfYear.asUtilDate)
         employeeStartDates <- getTeamMembershipStartDates(username)
      } yield {
         val workScheduleService = new WorkScheduleService(fullYearSchedules, fullYearReports,
           employeeStartDates.sorted.head, startOfYear.getYear)

         val totalWorkSchedule = workScheduleService.getWorkScheduleUntil(getEndDate(startOfYear.getYear).asUtilDate)

         val monthlyAccumulation = monthIterator(startOfYear, endOfYear)
           .map(month => workScheduleService.getWorkScheduleUntil(month
             .`with`(TemporalAdjusters.lastDayOfMonth()
             ).atTime(23, 59, 59).asUtilDate)).toList

         WorkScheduleQueryResult(username,
           workScheduleService.userStartOfYear,
           workScheduleService.workdaysThisYear,
           workScheduleService.userWorkdaysThisYear.round,
           workScheduleService.userWorkdaysAvailabilityRate,
           workScheduleService.vacationDaysThisYear,
           workScheduleService.usedVacationDays,
           workScheduleService.plannedVacationDays,
           workScheduleService.parentalLeaveDaysThisYear,
           workScheduleService.targetHoursThisYear,
           workScheduleService.burndownHoursPerWorkday,
           totalWorkSchedule,
           monthlyAccumulation)
      }
      resultFuture.pipeTo(requester)
  }

  private def getEndDate(year: Int) : LocalDateTime = {
    if (year == LocalDate.now().getYear) {
      LocalDate.now().atTime(23, 59)
    } else {
      LocalDate.ofYearDay(year, 1)
        .`with`(TemporalAdjusters.lastDayOfYear())
        .atTime(23, 59)
    }
  }

  private def monthIterator(start: LocalDate, end: LocalDate) = {
    Iterator.iterate(start)(_ plusMonths 1) takeWhile (_ isBefore end)
  }

  private def getWorkdaysFromUserSchedules(schedules: List[UserSchedule]) = {
    schedules.map(_.requiredHours).sum / 8
  }

  def getTeamMembershipStartDates(username: String): Future[List[Option[Date]]] = {
    val result = dbReader.getUserTeamMembershipDates(username)

    result.map(_.map(_.dateFrom))
  }
}
