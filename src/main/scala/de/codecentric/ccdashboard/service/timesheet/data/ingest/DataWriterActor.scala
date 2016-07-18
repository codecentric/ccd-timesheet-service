package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.access.DAO
import de.codecentric.ccdashboard.service.timesheet.data.schema.{Worklog, Worklogs}
import slick.driver.H2Driver

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by bjacobs on 13.07.16.
  */
trait DataWriterActor extends Actor with ActorLogging

abstract class BaseDataWriterActor(conf: Config) extends DataWriterActor {
  log.info("Instantiated")

  def receive = {
    case worklogs: Worklogs =>
      log.info(s"Received ${worklogs.get.size} worklogs to store")
      store(worklogs.get)
    case x => log.info(s"Received unknown message: $x")
  }

  def store(worklogs: Seq[Worklog]): Unit
}

class H2DataWriterActor(conf: Config) extends BaseDataWriterActor(conf) {
  log.info("Instantiating DAO")
  val dao = new DAO(H2Driver).worklogDAO

  import dao.driver.api._

  log.info("Instantiating DB")
  val db = Database.forConfig("h2mem1")
  val dbSetupFuture = db.run(dao.create)

  Await.result(dbSetupFuture, Duration.Inf)

  override def store(worklogs: Seq[Worklog]): Unit = {
    log.info(s"Storing to H2... ${worklogs.size}")
    val insertFuture = db.run(dao.insert(worklogs))
    val inserted = Await.result(insertFuture, Duration.Inf)
    log.info(s"Number of inserted elements: $inserted")
  }
}
