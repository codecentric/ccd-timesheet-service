package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, LocalDateTime}
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.google.common.reflect.TypeToken
import de.codecentric.ccdashboard.service.timesheet.data.access.WorkScheduleProviderActor.WorkScheduleQuery
import de.codecentric.ccdashboard.service.timesheet.data.model.{UserSchedule, UserUtilization}
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.concurrent.Future


object WorkScheduleProviderActor {
  case class WorkScheduleQuery(username: String, year: Option[Int])
}

/**
  * Created by tbinias on 22.12.16.
  */
class WorkScheduleProviderActor(cassandraContextConfig: CassandraContextConfig) extends Actor with ActorLogging {

  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig)
  import context.dispatcher
  import ctx._

  private val stringToken = TypeToken.of(classOf[String])
  private val dateToken = TypeToken.of(classOf[java.util.Date])

  implicit class DateRangeFilter(a: Date) {
    def >(b: Date) = quote(infix"$a > $b".as[Boolean])

    def >=(b: Date) = quote(infix"$a >= $b".as[Boolean])

    def <(b: Date) = quote(infix"$a < $b".as[Boolean])

    def <=(b: Date) = quote(infix"$a <= $b".as[Boolean])

    def ==(b: Date) = quote(infix"$a = $b".as[Boolean])
  }

  def receive: Receive = {
    case WorkScheduleQuery(username: String, year: Option[Int]) =>
      val requester = sender()
      log.debug("Received WorkScheduleQuery")
      val startOfYear = LocalDate.ofYearDay(year.getOrElse(LocalDate.now().getYear), 1)
      val endOfYear = startOfYear.`with`(TemporalAdjusters.lastDayOfYear())
      val resultFuture = for {
         fullYearSchedules <- ctx.run(userSchedule(username, startOfYear.asUtilDate, endOfYear.asUtilDate))
         fullYearReports <- ctx.run(userReport(username, startOfYear.asUtilDate, endOfYear.asUtilDate))
         employeeStartDates <- getTeamMembershipStartDates(username)
      } yield {
         val workScheduleService = new WorkScheduleService(fullYearSchedules, fullYearReports,
           employeeStartDates.sorted.headOption, startOfYear.getYear)

         val totalWorkSchedule = workScheduleService.getWorkScheduleUntil(getEndDate(startOfYear.getYear).asUtilDate)

         val monthlyAccumulation = monthIterator(startOfYear, endOfYear)
           .map(month => workScheduleService.getWorkScheduleUntil(month
             .`with`(TemporalAdjusters.lastDayOfMonth()
             ).atTime(23, 59, 59).asUtilDate)).toList

         WorkScheduleQueryResult(username,
           workScheduleService.userStartOfYear,
           workScheduleService.workDaysThisYear,
           workScheduleService.userWorkDaysThisYear.round,
           workScheduleService.userWorkDaysAvailabilityRate,
           workScheduleService.vacationDaysThisYear,
           workScheduleService.usedVacationDaysThisYear,
           workScheduleService.plannedVacationDaysThisYear,
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

  private def getWorkDaysFromUserSchedules(schedules: List[UserSchedule]) = {
    schedules.map(_.requiredHours).sum / 8
  }

  def userSchedule(username: String, from: Date, to: Date): Quoted[Query[UserSchedule]] = {
    query[UserSchedule]
      .filter(_.username == lift(username))
      .filter(_.workDate >= lift(from))
      .filter(_.workDate <= lift(to))
  }

  def userReport(username: String, from: Date, to: Date): Quoted[Query[UserUtilization]] = {
    query[UserUtilization]
      .filter(_.username == lift(username))
      .filter(_.day >= lift(from))
      .filter(_.day <= lift(to))
  }

  def getTeamMembershipStartDates(username: String): Future[List[Date]] = {
    val result = ctx.executeQuery(s"SELECT date_from FROM team_member WHERE name = '$username' ALLOW FILTERING;",
      extractor = row => row.get(0, dateToken))

    result.map(_.filterNot(_ == null))
  }
}
