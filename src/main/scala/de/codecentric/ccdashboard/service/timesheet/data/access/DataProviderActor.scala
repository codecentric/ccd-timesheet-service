package de.codecentric.ccdashboard.service.timesheet.data.access

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.{User, Worklog}
import de.codecentric.ccdashboard.service.timesheet.messages.{UserQuery, UserQueryResult, WorklogQuery, WorklogQueryResult}
import io.getquill.{CassandraAsyncContext, SnakeCase}

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  lazy val ctx = new CassandraAsyncContext[SnakeCase](dbConfigKey)

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

  def receive: Receive = {
    case WorklogQuery(username, from, to) =>
      val requester = sender
      log.info("Received WorklogQuery")

      val fut = ctx.run(worklogQuery(username, from, to))
      fut.map(WorklogQueryResult).pipeTo(requester)

    case UserQuery(username) =>
      val requester = sender
      log.info("Received UserQuery")
      val fut = ctx.run(userQuery.filter(_.userkey == lift(username)).take(1))
      fut.map(users => UserQueryResult(users.headOption)).pipeTo(requester)
  }
}
