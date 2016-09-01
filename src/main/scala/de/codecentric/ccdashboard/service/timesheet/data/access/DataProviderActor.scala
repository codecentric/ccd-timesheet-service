package de.codecentric.ccdashboard.service.timesheet.data.access

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.datastax.driver.core.TypeTokens
import com.google.common.reflect.TypeToken
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, User, Worklog}
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

  private val stringToken = TypeToken.of(classOf[String])
  private val stringMapToken = TypeTokens.mapOf(stringToken, stringToken)

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

  def issueQuery(id: String): Quoted[Query[Issue]] = {
    query[Issue].filter(_.id == lift(id))
  }

  def receive: Receive = {
    case WorklogQuery(username, from, to) =>
      val requester = sender
      log.info("Received WorklogQuery")
      ctx.run(worklogQuery(username, from, to))
        .map(WorklogQueryResult)
        .pipeTo(requester)

    case UserQuery(username) =>
      val requester = sender
      log.info("Received UserQuery")
      ctx.run(userQuery.filter(_.userkey == lift(username)).take(1))
        .map(users => UserQueryResult(users.headOption))
        .pipeTo(requester)

    case IssueQuery(id) =>
      val requester = sender
      log.info("Received IssueQuery")
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
  }
}
