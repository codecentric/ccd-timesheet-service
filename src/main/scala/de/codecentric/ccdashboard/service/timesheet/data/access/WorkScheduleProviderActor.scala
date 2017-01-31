package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, LocalDateTime}
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.datastax.driver.core.{Row, TypeTokens}
import com.google.common.reflect.TypeToken
import de.codecentric.ccdashboard.service.timesheet.data.model.{Team, UserSchedule, UserUtilization}
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.concurrent.Future


/**
  * Created by tbinias on 22.12.16.
  */
class WorkScheduleProviderActor(cassandraContextConfig: CassandraContextConfig) extends Actor with ActorLogging {

  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig)
  import context.dispatcher
  import ctx._

  private val stringToken = TypeToken.of(classOf[String])
  private val stringMapToken = TypeTokens.mapOf(stringToken, stringToken)
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
           .map(month => workScheduleService.getWorkScheduleUntil(month.`with`(TemporalAdjusters.lastDayOfMonth()).asUtilDate)).toList


         WorkScheduleQueryResult(username,
           workScheduleService.userStartOfYear,
           workScheduleService.workDaysThisYear,
           workScheduleService.userWorkDaysThisYear.round,
           workScheduleService.userWorkDaysAvailabilityRate,
           workScheduleService.vacationDaysThisYear,
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
      LocalDate.ofYearDay(year, 31).atTime(23, 59)
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

  private val teamExtractor: Row => Team = {
    row => {
      val id = row.getInt(0)
      val name = row.getString(1)
      Team(id, name)
    }
  }

  def getTeamMembershipStartDates(username: String): Future[List[Date]] = {
    ctx.executeQuery(s"SELECT date_from FROM team_member WHERE member_name = '$username' ALLOW FILTERING;",
      extractor = row => row.get(0, dateToken))
  }
}
