package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.LocalDate
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.datastax.driver.core.{Row, TypeTokens}
import com.google.common.reflect.TypeToken
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.messages._
import io.getquill.{CassandraAsyncContext, SnakeCase}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  lazy val ctx = new CassandraAsyncContext[SnakeCase](dbConfigKey)

  val importStartDate = LocalDate.parse(conf.getString("timesheet-service.data-import.start-date"))

  private val stringToken = TypeToken.of(classOf[String])
  private val stringMapToken = TypeTokens.mapOf(stringToken, stringToken)
  private val dateToken = TypeToken.of(classOf[java.util.Date])

  private var userQueryCount = 0L
  private var teamQueryCount = 0L
  private var teamMembershipQueryCount = 0L
  private var worklogQueryCount = 0L
  private var issueQueryCount = 0L

  private val teamExtractor: Row => Team = { row => {
    val id = row.getInt(0)
    val name = row.getString(1)
    val map = row.getMap(2, stringToken, dateToken).asScala.toMap.mapValues(d => if (d.getTime == 0) None else Some(d))

    Team(id, name, Some(map))
  }
  }

  private val issueExtractor: Row => Issue = { row => {
    val issueId = row.getString(0)
    val issueKey = row.getString(1)
    val issueUrl = row.getString(2)
    val summary = Option(row.getString(3))
    val component = row.getMap(4, stringToken, stringToken).asScala.toMap
    val dailyRate = Option(row.getString(5))
    val invoicing = row.getMap(6, stringToken, stringToken).asScala.toMap
    val issueType = row.getMap(6, stringToken, stringToken).asScala.toMap

    Issue(issueId, issueKey, issueUrl, summary, component, dailyRate, invoicing, issueType)
  }
  }

  import context.dispatcher
  import ctx._

  implicit class DateRangeFilter(a: Date) {
    def >(b: Date) = quote(infix"$a > $b".as[Boolean])

    def >=(b: Date) = quote(infix"$a >= $b".as[Boolean])

    def <(b: Date) = quote(infix"$a < $b".as[Boolean])

    def <=(b: Date) = quote(infix"$a <= $b".as[Boolean])

    def ==(b: Date) = quote(infix"$a = $b".as[Boolean])
  }

  def worklogQuery(username: String): Quoted[Query[Worklog]] = {
    query[Worklog].filter(_.username == lift(username))
  }

  def worklogQuery(username: String, from: Option[Date], to: Option[Date]): Quoted[Query[Worklog]] = {
    (from, to) match {
      case (Some(a), Some(b)) => worklogQuery(username).filter(_.workDate >= lift(a)).filter(_.workDate <= lift(b))
      case (Some(a), None) => worklogQuery(username).filter(_.workDate >= lift(a))
      case (None, Some(b)) => worklogQuery(username).filter(_.workDate <= lift(b))
      case (None, None) => worklogQuery(username)
    }
  }

  val userQuery = quote {
    query[User]
  }

  val teamQuery = quote {
    query[Team]
  }

  def userReport(username: String, from: Date, to: Date): Quoted[Query[UserUtilization]] = {
    query[UserUtilization]
      .filter(_.username == lift(username))
      .filter(_.day >= lift(from))
      .filter(_.day <= lift(to))
  }

  def userSchedule(username: String, from: Date, to: Date): Quoted[Query[UserSchedule]] = {
    query[UserSchedule]
      .filter(_.username == lift(username))
      .filter(_.workDate >= lift(from))
      .filter(_.workDate <= lift(to))
  }

  def userScheduleQuery(username: String, from: Date, to: Date) = {
    ctx.run(userSchedule(username, from, to))
  }

  def teamMembershipQuery(username: String) = {
    ctx.executeQuerySingle(s"SELECT id, name, members FROM team WHERE members contains key '$username'",
      extractor = teamExtractor)
      .map(team => (username, team.id, team.name, team.members.flatMap(_.get(username)).flatten))
      .map(TeamMembershipQueryResult.tupled)
  }

  def issueQuery(id: String): Quoted[Query[Issue]] = {
    query[Issue].filter(_.id == lift(id))
  }

  def receive: Receive = {
    case WorklogQuery(username, from, to) =>
      val requester = sender()
      log.debug("Received WorklogQuery")
      ctx.run(worklogQuery(username, from, to))
        .map(WorklogQueryResult)
        .pipeTo(requester)
      worklogQueryCount = worklogQueryCount + 1

    case UserQuery(username) =>
      val requester = sender()
      log.debug("Received UserQuery")
      ctx.run(userQuery.filter(_.name == lift(username)).take(1))
        .map(users => UserQueryResult(users.headOption))
        .pipeTo(requester)
      userQueryCount = userQueryCount + 1

    case IssueQuery(id) =>
      val requester = sender()
      log.debug("Received IssueQuery")
      val result = ctx.executeQuerySingle[Issue](s"SELECT id, issue_key, issue_url, summary, components, custom_fields, issue_type FROM issue WHERE id = '$id'",
        extractor = issueExtractor)

      result.onComplete {
        case Success(issue) => requester ! IssueQueryResult(Some(issue))
        case Failure(ex) => requester ! IssueQueryResult(None)
      }
      issueQueryCount = issueQueryCount + 1

    case TeamQuery(teamId) =>
      val requester = sender()
      log.debug("Received TeamQuery")
      teamId match {
        case Some(id) =>
          ctx.executeQuerySingle(s"SELECT id, name, members FROM team WHERE id = $id",
            extractor = teamExtractor
          ).map(t => TeamQueryResponse(Some(Teams(List(t)))))
            .pipeTo(requester)

        case None =>
          ctx.executeQuery(s"SELECT id, name, members FROM team",
            extractor = teamExtractor
          ).map(t => TeamQueryResponse(Some(Teams(t))))
            .pipeTo(requester)
      }

      teamQueryCount = teamQueryCount + 1

    case TeamMembershipQuery(username) =>
      val requester = sender()
      log.debug("Received TeamMembershipQuery")

      teamMembershipQuery(username).pipeTo(requester)

      teamMembershipQueryCount = teamMembershipQueryCount + 1

    case UserReportQuery(username, from, to, aggregationType) =>
      val requester = sender()

      val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
      val toDate = to.getOrElse(new Date())

      val employeeSinceDateFuture = teamMembershipQuery(username).map(_.dateFrom)

      val dateToUse = for {
        employeeSinceDateOption <- employeeSinceDateFuture
      } yield {
        employeeSinceDateOption match {
          case Some(employeeSinceDate) =>
            if (employeeSinceDate.after(fromDate)) employeeSinceDate else fromDate
          case None =>
            fromDate
        }
      }

      val utilizationReportsFuture = dateToUse.flatMap(date => ctx.run(userReport(username, date, toDate)))

      val workScheduleFuture = dateToUse.flatMap(date => userScheduleQuery(username, date, toDate))

      // Map utilization reports to general Reports
      val reportsFuture = utilizationReportsFuture
        .map(_.map(u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours, u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours, u.otherHours))))

      val aggregationResultFuture = for {
        reports <- reportsFuture
        workSchedule <- workScheduleFuture
      } yield {
        val aggregator = ReportAggregator(reports, workSchedule)

        // Aggregate Reports
        aggregationType match {
          case UserReportQueryAggregationType.DAILY =>
            aggregator.aggregateDaily()

          case UserReportQueryAggregationType.MONTHLY =>
            aggregator.aggregateMonthly()

          case UserReportQueryAggregationType.YEARLY =>
            aggregator.aggregateYearly()
        }
      }

      val resultFuture = for {
        date <- dateToUse
        aggregationResult <- aggregationResultFuture
      } yield {
        UserReportQueryResponse(date, toDate, aggregationType.toString, aggregationResult)
      }

      resultFuture.pipeTo(requester)
  }
}
