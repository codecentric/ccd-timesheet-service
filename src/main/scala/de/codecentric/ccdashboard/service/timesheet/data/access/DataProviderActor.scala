package de.codecentric.ccdashboard.service.timesheet.data.access

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.messages.{WorklogQuery, WorklogQueryResult}
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver

/**
  * Created by bjacobs on 18.07.16.
  */
class DataProviderActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  val dbConfig = DatabaseConfig.forConfig[JdbcDriver](dbConfigKey)
  val db = dbConfig.db
  val dao = new DAO(dbConfig.driver).worklogDAO

  import context.dispatcher

  def receive: Receive = {
    case WorklogQuery(x) =>
      log.info("Received WorklogQuery")
      val requester = sender

      val queryResultFuture = db.run(dao.getFirst(x)).map {
        worklogs => {
          worklogs.foreach(x => log.info(x.toString))
          requester ! WorklogQueryResult(worklogs)
        }
      }
  }
}
