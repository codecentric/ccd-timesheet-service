package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.model._
import io.getquill.{CassandraSyncContext, SnakeCase}

/**
  * Actor that receives Worklogs from a DataIngestActor and stores inserts them into the database
  */
class DataWriterActor(conf: Config) extends Actor with ActorLogging {

  import context.dispatcher
  import de.codecentric.ccdashboard.service.timesheet.data.encoding._

  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  val worklogTableName = conf.getString("timesheet-service.tablenames.worklogs")

  lazy val ctx = new CassandraSyncContext[SnakeCase](dbConfigKey)

  import ctx._

  def insert(w: List[Worklog]) = quote {
    liftQuery(w).foreach(worklog => {
      // Until this is fixed, table name is hardcoded. See https://github.com/getquill/quill/issues/501
      // query[JiraWorklog].schema(_.entity(worklogTableName)).insert(worklog)
      query[Worklog].insert(worklog)
    })
  }

  def receive = {
    case w: Worklogs =>
      val worklogs = w.get.toList
      log.info(s"Received ${worklogs.size} worklogs to store")

      ctx.run(insert(worklogs))

    case x => log.warning(s"Received unknown message: $x")
  }
}