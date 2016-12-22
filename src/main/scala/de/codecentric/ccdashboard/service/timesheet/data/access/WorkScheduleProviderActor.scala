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

  def receive: Receive = {
    case WorkScheduleQuery(username: String) =>
      val requester = sender()
      log.debug("Received WorkScheduleQuery")
      val startOfYear = LocalDate.now().withDayOfYear(1);
      val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      val resultFuture = for {
         fullYearUserSchedules <- ctx.run(userSchedule(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         fullYearUserReports <- ctx.run(userReport(username, asUtilDate(startOfYear), asUtilDate(endOfYear)))
         employeeSinceOption <- teamMembershipQuery(username).map(_.flatMap(_.dateFrom))
      } yield {
         val employeeSince = employeeSinceOption.headOption.getOrElse(asUtilDate(startOfYear))
         val overallWorkDaysThisYear = getWorkDaysFromUserSchedules(fullYearUserSchedules)
         val scaledUserSchedules = fullYearUserSchedules.filter(s => s.workDate.after(employeeSince) ||
                                                                     s.workDate.equals(employeeSince))

         val parentalLeaveDays = fullYearUserReports.flatMap(_.parentalLeaveHours).sum / 8
         val scaledWorkDaysThisYear = getWorkDaysFromUserSchedules(scaledUserSchedules)
         val scaledTargetHours = TARGET_HOURS_BASE - (parentalLeaveDays * 8 * 0.8)
         val targetHours = scaledTargetHours * scaledWorkDaysThisYear / overallWorkDaysThisYear

         val endOfToday = asUtilDate(LocalDate.now().atTime(23, 59))
         val workDaysTillTodayInclusive = getWorkDaysFromUserSchedules(scaledUserSchedules.filter(_.workDate.before(endOfToday)))

         val today = asUtilDate(LocalDate.now())
         val usedVacationDaysTillToday = fullYearUserReports.filter(_.day.before(today)).flatMap(_.vacationHours).sum / 8
         val burndownHoursPerWorkday = targetHours / scaledWorkDaysThisYear
         val targetHoursToday = (workDaysTillTodayInclusive - usedVacationDaysTillToday) * burndownHoursPerWorkday

         WorkScheduleQueryResult(username, overallWorkDaysThisYear.round, parentalLeaveDays, usedVacationDaysTillToday,
           scaledWorkDaysThisYear.round, targetHours, targetHoursToday)
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

  private def getVacationHours(reports :List[(Date, ReportEntry)]) = {
    val today = asUtilDate(LocalDate.now())
    val tomorrow = asUtilDate(LocalDate.now().plusDays(1))
    val usedHours = reports.filter(_._1.before(tomorrow)).flatMap(_._2.vacationHours).sum
    val plannedHours = reports.filter(_._1.after(today)).flatMap(_._2.vacationHours).sum
    VacationHours(usedHours, plannedHours, 30 * 8 - usedHours - plannedHours)
  }
}
