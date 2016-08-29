package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.access.DAO
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import slick.backend.DatabaseConfig
import slick.driver.JdbcDriver

/**
  * Actor that receives Worklogs from a DataIngestActor and stores inserts them into the database
  */
class DataWriterActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  val dbConfig = DatabaseConfig.forConfig[JdbcDriver](dbConfigKey)
  val db = dbConfig.db
  val dao = new DAO(dbConfig.driver).worklogDAO

  import context.dispatcher

  val dbSetupFuture = db.run(dao.create)
  dbSetupFuture.onComplete(_ => log.info("Instantiated"))

  def receive = {
    case worklogs: Worklogs =>
      log.info(s"Received ${worklogs.get.size} worklogs to store")
    /*
          val insertFuture = db.run(dao.insert(worklogs.get))
          insertFuture.onSuccess {
            case Some(i: Int) => log.info(s"Number of inserted elements: $i")
            case None => log.info("No elements inserted.")
          }
    */

    case x => log.info(s"Received unknown message: $x")
  }
}