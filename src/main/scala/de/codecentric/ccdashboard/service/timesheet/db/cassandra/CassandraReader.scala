package de.codecentric.ccdashboard.service.timesheet.db.cassandra

import java.util.Date

import com.datastax.driver.core.{Row, SocketOptions}
import com.google.common.reflect.TypeToken
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.model.{Team, User, _}
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader
import de.codecentric.ccdashboard.service.timesheet.messages._
import de.codecentric.ccdashboard.service.timesheet.util.CassandraContextConfigWithOptions
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CassandraReader extends DatabaseReader {

  lazy val cassandraContextConfig: CassandraContextConfig = createCassandraContext()

  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig)

  import ctx._

  implicit class DateRangeFilter(a: Date) {
    def >(b: Date) = quote(infix"$a > $b".as[Boolean])

    def >=(b: Date) = quote(infix"$a >= $b".as[Boolean])

    def <(b: Date) = quote(infix"$a < $b".as[Boolean])

    def <=(b: Date) = quote(infix"$a <= $b".as[Boolean])

    def ==(b: Date) = quote(infix"$a = $b".as[Boolean])
  }

  private val stringToken = TypeToken.of(classOf[String])
  private val dateToken = TypeToken.of(classOf[java.util.Date])

  private val teamExtractor = (row: Row) => {
    val id = row.getInt(0)
    val name = row.getString(1)

    Team(id, name)
  }

  private val issueExtractor = (row: Row) => {
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

  private val teamMemberExtractor = (row: Row)  => {
    val memberName = row.getString("name")
    val dateFrom = row.getTimestamp("date_from")
    val dateTo = row.getTimestamp("date_to")
    val availability = row.getInt("availability")
    val teamId = row.getInt("team_id")

    TeamMember(teamId, memberName, Option(dateFrom), Option(dateTo), Option(availability))
  }

  def getTeamById(id: Int): Future[Team] = {
    ctx.executeQuerySingle(s"SELECT id, name FROM team WHERE id = $id",
      extractor = teamExtractor
    )
  }

  def getIssueById(id: String): Future[Issue] = {
    ctx.executeQuerySingle[Issue](s"SELECT id, issue_key, issue_url, summary, components, custom_fields, issue_type FROM issue WHERE id = '$id'",
      extractor = issueExtractor)
  }

  def getEmployees(): Future[EmployeesQueryResponse] = {
    ctx.executeQuery[String]("SELECT name from team_member",
          extractor = row => row.getString("name")
    ) .map(_.distinct.sorted)
      .map(EmployeesQueryResponse)
  }

  def getTeamMembers(teamId: Int): Future[List[TeamMember]] = {
    ctx.executeQuery[TeamMember](
      s"SELECT team_id, name, date_from, date_to, availability FROM team_member WHERE team_id = $teamId",
      extractor = teamMemberExtractor)
  }

  def getTeamIds(): Future[List[Int]] = {
    ctx.executeQuery[Int]("SELECT DISTINCT team_id FROM team_member", extractor = row => row.getInt("team_id"))
  }

  def getUserSchedules(username: String, from: Date, to: Date): Future[List[UserSchedule]] = {
    ctx.run(userSchedule(username, from, to))
  }

  def getWorklog(username: String, from: Option[Date], to: Option[Date]): Future[WorklogQueryResult] = {
    ctx.run(worklogQuery(username, from, to))
      .map(WorklogQueryResult)
  }

  def getUtilizationReport(username: String, from: Date, to: Date): Future[List[UserUtilization]] = {
    ctx.run(userReport(username, from, to))
  }

  def getUserSchedule(username: String, from: Date, to: Date): Future[List[UserSchedule]] = {
    ctx.run(userSchedule(username, from, to))
  }

  def getUserTeamMembershipDates(username: String): Future[List[TeamMember]] = {
    val result = ctx.executeQuery[TeamMember](
      s"SELECT team_id, name, date_from, date_to, availability FROM team_member WHERE name = '$username' ALLOW FILTERING;",
      extractor = teamMemberExtractor)

    // TODO fix by using quill features
    result.map(_.filterNot(_ == null))
  }

  def getUserByName(username: String): Future[Option[User]] = {
    val userQuery = quote(query[User])
    ctx.run(userQuery.filter(_.name == lift(username)).take(1)).map(_.headOption)
  }

  private def userSchedule(username: String, from: Date, to: Date): Quoted[Query[UserSchedule]] = {
    query[UserSchedule]
      .filter(_.username == lift(username))
      .filter(_.workDate >= lift(from))
      .filter(_.workDate <= lift(to))
  }

  private def userReport(username: String, from: Date, to: Date): Quoted[Query[UserUtilization]] = {
    query[UserUtilization]
      .filter(_.username == lift(username))
      .filter(_.day >= lift(from))
      .filter(_.day <= lift(to))
  }

  private def worklogQuery(username: String): Quoted[Query[Worklog]] = {
    query[Worklog].filter(_.username == lift(username))
  }

  private def worklogQuery(username: String, from: Option[Date], to: Option[Date]): Quoted[Query[Worklog]] = {
    (from, to) match {
      case (Some(a), Some(b)) => worklogQuery(username).filter(_.workDate >= lift(a)).filter(_.workDate <= lift(b))
      case (Some(a), None) => worklogQuery(username).filter(_.workDate >= lift(a))
      case (None, Some(b)) => worklogQuery(username).filter(_.workDate <= lift(b))
      case (None, None) => worklogQuery(username)
    }
  }

  private def createCassandraContext(): CassandraContextConfig = {
    val conf = ConfigFactory.load()
    val dbConfigKey = conf.getString("timesheet-service.database-config-key")
    val dbConfig = ConfigFactory.load().getConfig(dbConfigKey)
    val socketOptions = new SocketOptions().setConnectTimeoutMillis(60000).setReadTimeoutMillis(60000)
    new CassandraContextConfigWithOptions(dbConfig, socketOptions = Some(socketOptions))
  }

}