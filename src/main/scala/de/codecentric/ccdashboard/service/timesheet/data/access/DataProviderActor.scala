package de.codecentric.ccdashboard.service.timesheet.data.access

import akka.actor.{Actor, ActorLogging}
import akka.pattern.pipe
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.encoding._
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklog
import de.codecentric.ccdashboard.service.timesheet.messages.{WorklogQuery, WorklogQueryResult}
import io.getquill.{CassandraAsyncContext, SnakeCase}

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  lazy val ctx = new CassandraAsyncContext[SnakeCase](dbConfigKey)

  import context.dispatcher
  import ctx._

  val worklogQuery = quote {
    query[Worklog]
  }

  def receive: Receive = {
    case WorklogQuery(x) =>
      val requester = sender
      log.info("Received WorklogQuery")
      val fut = ctx.run(worklogQuery.take(lift(x)))
      fut.map(WorklogQueryResult).pipeTo(requester)
  }
}
