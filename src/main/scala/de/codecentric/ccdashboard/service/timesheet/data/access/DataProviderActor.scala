package de.codecentric.ccdashboard.service.timesheet.data.access

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklog
import de.codecentric.ccdashboard.service.timesheet.messages.{WorklogQuery, WorklogQueryResult}
import io.getquill.{CassandraSyncContext, SnakeCase}


/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {

  import de.codecentric.ccdashboard.service.timesheet.data.encoding._

  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  lazy val ctx = new CassandraSyncContext[SnakeCase](dbConfigKey)

  import context.dispatcher
  import ctx._

  def receive: Receive = {
    case WorklogQuery(x) =>
      log.info("Received WorklogQuery")
      val requester = sender

      val res = ctx.run(quote {
        query[Worklog].take(lift(x))
      })

      requester ! WorklogQueryResult(res)
  }
}
