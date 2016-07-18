package de.codecentric.ccdashboard.service.timesheet.data.server

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.WorklogQuery
import de.codecentric.ccdashboard.service.timesheet.data.access.DAO
import slick.driver.H2Driver

import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
  * Created by bjacobs on 18.07.16.
  */
trait DataServerActor extends Actor with ActorLogging

abstract class BaseDataServerActor(conf: Config) extends DataServerActor

class H2DataServerActor(conf: Config) extends BaseDataServerActor(conf) {
  val dao = new DAO(H2Driver).worklogDAO

  import dao.driver.api._

  log.info("Instantiating DB")
  val db = Database.forConfig("h2mem1")

  def receive: Receive = {
    case WorklogQuery(x) =>
      val queryResultFuture = db.run(dao.getFirst(x))
      val queryResult = Await.result(queryResultFuture, Duration.Inf)
      queryResult.foreach(x => log.info(x.toString))
  }
}
