package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time.{LocalDateTime, ZoneId}
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
import io.getquill.MappedEncoding

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  lazy val ctx = new CassandraAsyncContext[SnakeCase](dbConfigKey)

  private val stringToken = TypeToken.of(classOf[String])
  private val stringMapToken = TypeTokens.mapOf(stringToken, stringToken)
  private val dateToken = TypeToken.of(classOf[java.util.Date])

  private val teamExtractor: Row => Team = { row => {
    val id = row.getInt(0)
    val name = row.getString(1)
    val map = row.getMap(2, stringToken, dateToken).asScala.toMap.mapValues(d => if (d.getTime == 0) None else Some(d))

    Team(id, name, Some(map))
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

  def issueQuery(id: String): Quoted[Query[Issue]] = {
    query[Issue].filter(_.id == lift(id))
  }

  def receive: Receive = {
    case WorklogQuery(username, from, to) =>
      val requester = sender
      log.debug("Received WorklogQuery")
      ctx.run(worklogQuery(username, from, to))
        .map(WorklogQueryResult)
        .pipeTo(requester)

    case UserQuery(username) =>
      val requester = sender
      log.debug("Received UserQuery")
      ctx.run(userQuery.filter(_.userkey == lift(username)).take(1))
        .map(users => UserQueryResult(users.headOption))
        .pipeTo(requester)

    case IssueQuery(id) =>
      val requester = sender
      log.debug("Received IssueQuery")
      val result = ctx.executeQuerySingle[Issue](s"SELECT id, issue_key, issue_url, summary, components, custom_fields, issue_type FROM issue WHERE id = '$id'",
        extractor = {
          row => {
            val issueId = row.getString(0)
            val issueKey = row.getString(1)
            val issueUrl = row.getString(2)
            val summary = if ("" == row.getString(3)) None else Some(row.getString(3))
            val components = row.getMap(4, stringToken, stringToken).asScala.toMap
            val customFields = row.getMap(5, stringToken, stringMapToken).asScala.mapValues(_.asScala.toMap).toMap
            val issueType = row.getMap(6, stringToken, stringToken).asScala.toMap

            Issue(issueId, issueKey, issueUrl, summary, components, customFields, issueType)
          }
        }
      )

      result.onComplete {
        case Success(issue) => requester ! IssueQueryResult(Some(issue))
        case Failure(ex) => requester ! IssueQueryResult(None)
      }

    case TeamQuery(teamId) =>
      val requester = sender
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
  }
}
