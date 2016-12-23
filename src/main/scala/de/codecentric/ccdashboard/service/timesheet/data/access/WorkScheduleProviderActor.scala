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


  final def TARGET_HOURS_BASE = 1440
  final def VACATION_DAYS_PER_YEAR = 30


  def receive: Receive = {
    case WorkScheduleQuery(username: String, until: Option[LocalDate]) =>
      val requester = sender()
      log.debug("Received WorkScheduleQuery")
      val startOfYear = LocalDate.now().withDayOfYear(1);
      val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      val resultFuture = for {
         fullYearSchedules <- ctx.run(userSchedule(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         fullYearReports <- ctx.run(userReport(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         employeeSinceOption <- teamMembershipQuery(username).map(_.flatMap(_.dateFrom))
      } yield {
         val userStartOfYear = employeeSinceOption.headOption.getOrElse(asUtilDate(startOfYear))
         val userMonthsThisYear = ChronoUnit.MONTHS.between(asLocalDate(userStartOfYear), endOfYear) +
           (if (asLocalDate(userStartOfYear).getDayOfMonth == 15) 0.5 else 1)
         val vacationDaysThisYear = (userMonthsThisYear * VACATION_DAYS_PER_YEAR / 12).round

         val workDaysThisYear = fullYearSchedules.filter(_.requiredHours > 0).size
         val userSchedules = fullYearSchedules.filter(s => s.workDate.after(userStartOfYear) ||
                                                      s.workDate.equals(userStartOfYear))
         val userWorkDaysThisYear = getWorkDaysFromUserSchedules(userSchedules)
         val userWorkDaysAvailabilityRate = userWorkDaysThisYear / workDaysThisYear

         val parentalLeaveDaysThisYear = fullYearReports.flatMap(_.parentalLeaveHours).sum / 8
         val targetHours = (TARGET_HOURS_BASE * userWorkDaysAvailabilityRate) - (parentalLeaveDaysThisYear * 8 * 0.8)

         val endDate = asUtilDate(until.getOrElse(LocalDate.now).atTime(23, 59))
         val workDaysTillTodayInclusive = getWorkDaysFromUserSchedules(userSchedules.filter(_.workDate.before(endDate)))

         val burndownHoursPerWorkday = targetHours / (userWorkDaysThisYear - vacationDaysThisYear - parentalLeaveDaysThisYear)

         val reportsTillTodayInclusive = fullYearReports.filter(_.day.before(endDate))
         val usedVacationDaysTillTodayInclusive = reportsTillTodayInclusive.flatMap(_.vacationHours).sum / 8
         val usedParentalLeaveDaysTillTodayInclusive = reportsTillTodayInclusive.flatMap(_.parentalLeaveHours).sum / 8
         val targetHoursToday =
           (workDaysTillTodayInclusive - usedVacationDaysTillTodayInclusive - usedParentalLeaveDaysTillTodayInclusive) * burndownHoursPerWorkday

         WorkScheduleQueryResult(username,
           userStartOfYear,
           workDaysThisYear,
           userWorkDaysThisYear.round,
           userWorkDaysAvailabilityRate,
           vacationDaysThisYear,
           usedVacationDaysTillTodayInclusive,
           parentalLeaveDaysThisYear,
           usedParentalLeaveDaysTillTodayInclusive,
           targetHours,
           targetHoursToday,
           burndownHoursPerWorkday)
      }
      resultFuture.pipeTo(requester)
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
