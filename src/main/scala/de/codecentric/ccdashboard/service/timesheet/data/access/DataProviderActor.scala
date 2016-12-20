package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{LocalDate, ZoneId}
import java.time.temporal.{TemporalAccessor, TemporalAdjusters}
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import akka.pattern.ask
import akka.util.Timeout
import com.datastax.driver.core.{Row, TypeTokens}
import com.google.common.reflect.TypeToken
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.messages._
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._


/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config, cassandraContextConfig: CassandraContextConfig) extends Actor with ActorLogging {
  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig)

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
    ctx.executeQuery(s"SELECT id, name, members FROM team WHERE members contains key '$username'",
      extractor = teamExtractor)
      .map(teams => teams.map(team => TeamMembershipQueryResult(username, team.id, team.name, team.members.flatMap(_.get(username)).flatten)))
  }

  def issueQuery(id: String): Quoted[Query[Issue]] = {
    query[Issue].filter(_.id == lift(id))
  }

  def getEmployeeSpecificDateRange(from: Option[Date], to: Option[Date], username: String) = {
    val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
    val toDate = to.getOrElse(new Date())

    val employeeSinceDateFuture = teamMembershipQuery(username).map(_.flatMap(_.dateFrom))

    val dateToUse = for {
      employeeSinceDateList <- employeeSinceDateFuture
    } yield {
      employeeSinceDateList match {
        case Nil => fromDate
        case list => List(list.min, fromDate).max
      }
    }
    (dateToUse, toDate)
  }

  def aggregateReportsWithSchedules(reports: List[(Date, ReportEntry)], workSchedule: List[UserSchedule], aggregationType: ReportQueryAggregationType.Value) = {
    val aggregator = ReportAggregator(reports, workSchedule)
    // Aggregate Reports
    aggregationType match {
      case ReportQueryAggregationType.DAILY =>
        aggregator.aggregateDaily()

      case ReportQueryAggregationType.MONTHLY =>
        aggregator.aggregateMonthly()

      case ReportQueryAggregationType.YEARLY =>
        aggregator.aggregateYearly()
    }
  }


  def generateReportQueryResponse(fromDate: Date, toDate: Date, aggregationResultFuture: Future[ReportAggregationResult], aggregationType: ReportQueryAggregationType.Value) =
    aggregationResultFuture.map { aggregationResult => {
      ReportQueryResponse(fromDate, toDate, aggregationType.toString, aggregationResult)
    }
    }

  private def asUtilDate(localDate :LocalDate): Date = {
    Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())
  }

  private def getVacationHours(reports :List[(Date, ReportEntry)]) = {
    val today = asUtilDate(LocalDate.now())
    val tomorrow = asUtilDate(LocalDate.now().plusDays(1))
    val usedHours = reports.filter(_._1.before(tomorrow)).flatMap(_._2.vacationHours).sum
    val plannedHours = reports.filter(_._1.after(today)).flatMap(_._2.vacationHours).sum
    VacationHours(usedHours, plannedHours, 30 * 8 - usedHours - plannedHours)
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

      val startOfYear = LocalDate.now().withDayOfYear(1);
      val endOfYear = LocalDate.now().`with`(TemporalAdjusters.lastDayOfYear())
      val (fromDate, toDate) = getEmployeeSpecificDateRange(Option(asUtilDate(startOfYear)), Option(asUtilDate(endOfYear)), username)

      val resultFut = for {
        date <- fromDate
        utilizationReports <- ctx.run(userReport(username, date, toDate))
        user <- ctx.run(userQuery.filter(_.name == lift(username)).take(1)).map(users => users.headOption)
      } yield {
        val reports = utilizationReports.map(u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours, u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours, u.otherHours)))
        UserQueryResult(user, getVacationHours(reports))
      }

      resultFut.pipeTo(requester)
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

      val (fromDate, toDate) = getEmployeeSpecificDateRange(from, to, username)

      val resultFut = for {
        date <- fromDate
        utilizationReports <- ctx.run(userReport(username, date, toDate))
        workSchedule <- ctx.run(userSchedule(username, date, toDate))
      } yield {
        val reports = utilizationReports.map(u => (u.day, ReportEntry(u.billableHours, u.adminHours, u.vacationHours, u.preSalesHours, u.recruitingHours, u.illnessHours, u.travelTimeHours, u.twentyPercentHours, u.absenceHours, u.parentalLeaveHours, u.otherHours)))
        val aggregationResult = aggregateReportsWithSchedules(reports, workSchedule, aggregationType)

        ReportQueryResponse(date, toDate, aggregationType.toString, aggregationResult)
      }

      resultFut.pipeTo(requester)

    case TeamReportQuery(teamId, from, to, aggregationType) =>
      val requester = sender()

      val fromDate = from.getOrElse(localDateEncoder.f(importStartDate))
      val toDate = to.getOrElse(new Date())

      // get all users from the team
      val teamFut = ctx.executeQuerySingle(s"SELECT id, name, members FROM team WHERE id = $teamId",
        extractor = teamExtractor
      )

      teamFut.map(team => {
        val usernames = team.members.map(_.keys).getOrElse(Nil)

        implicit val timeout = Timeout(60.seconds)

        val usersReportsFut = Future.sequence(usernames.map(username => {
          (self ? UserReportQuery(username, from, to, aggregationType)).mapTo[ReportQueryResponse]
        }))

        val resultFut = for {
          allReportAggregations <- usersReportsFut.map(_.map(_.result))
        } yield {
          val allReportAggregationsList = allReportAggregations.toList
          val allReports = allReportAggregationsList.flatMap(_.reports)
          val size = allReportAggregationsList.size
          val overallHoursRequiredList = allReportAggregationsList.map(_.overallHoursRequired)
          val overallBillableHoursList = allReportAggregationsList.map(_.overallBillableHours)
          val overallUtilizationList = allReportAggregationsList.map(_.overallUtilization)
          val keyGroupedReports = allReports.groupBy(_.key)

          val teamReportAggregation = keyGroupedReports.map {
            case (key, reportAggregations) =>
              val reports = reportAggregations.map(_.report)
              val utilization = reportAggregations.map(_.utilization)
              val numberOfConsultants = reports.size

              val reducedReports = reports.reduce((l, r) => l + r)
              val reducedUtilization = utilization.sum / utilization.size

              ReportAggregation(key, reducedReports, reducedUtilization, numberOfConsultants)
          }

          val overallHoursRequired = overallHoursRequiredList.sum
          val overallBillableHours = overallBillableHoursList.sum
          val overallUtilization = overallUtilizationList.sum / size
          val result = ReportAggregationResult(overallHoursRequired, overallBillableHours, overallUtilization, List[Date](), teamReportAggregation.toList.sortBy(_.key))
          ReportQueryResponse(fromDate, toDate, aggregationType.toString, result)
        }

        resultFut.pipeTo(requester)
      }
      )
  }
}
