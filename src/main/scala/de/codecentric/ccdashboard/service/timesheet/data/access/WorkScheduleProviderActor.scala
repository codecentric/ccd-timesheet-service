package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.time.temporal.{ChronoUnit, TemporalAdjusters}
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.datastax.driver.core.{Row, TypeTokens}
import com.google.common.reflect.TypeToken
import de.codecentric.ccdashboard.service.timesheet.data.model.{Team, UserSchedule, UserUtilization}
import de.codecentric.ccdashboard.service.timesheet.messages._
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.collection.JavaConverters._


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
    case WorkScheduleQuery(username: String) =>
      val requester = sender()
      log.debug("Received WorkScheduleQuery")
      val startOfYear = LocalDate.now().withDayOfYear(1)
      val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      val resultFuture = for {
         fullYearSchedules <- ctx.run(userSchedule(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         fullYearReports <- ctx.run(userReport(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         employeeSince <- teamMembershipQuery(username).map(_.flatMap(_.dateFrom))
      } yield {
         val workScheduleService = new WorkScheduleService(fullYearSchedules, fullYearReports, employeeSince.headOption)

         val overallWorkSchedule = workScheduleService.getWorkScheduleUntil(asUtilDate(LocalDate.now().atTime(23, 59)))

         val monthlyAccumulation = monthIterator(startOfYear, endOfYear)
                                    .map(month => workScheduleService.getWorkScheduleUntil(asUtilDate(month))).toList


         WorkScheduleQueryResult(username,
           workScheduleService.userStartOfYear,
           workScheduleService.workDaysThisYear,
           workScheduleService.userWorkDaysThisYear.round,
           workScheduleService.userWorkDaysAvailabilityRate,
           workScheduleService.vacationDaysThisYear,
           workScheduleService.parentalLeaveDaysThisYear,
           workScheduleService.targetHoursThisYear,
           workScheduleService.burndownHoursPerWorkday,
           overallWorkSchedule,
           monthlyAccumulation)
      }
      resultFuture.pipeTo(requester)
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
      val map = row.getMap(2, stringToken, dateToken).asScala.toMap.mapValues(d => if (d.getTime == 0) None else Some(d))
      Team(id, name, Some(map))
    }
  }

  def teamMembershipQuery(username: String) = {
    ctx.executeQuery(s"SELECT id, name, members FROM team WHERE members contains key '$username'",
      extractor = teamExtractor)
      .map(teams => teams.map(team => TeamMembershipQueryResult(username, team.id, team.name, team.members.flatMap(_.get(username)).flatten)))
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
