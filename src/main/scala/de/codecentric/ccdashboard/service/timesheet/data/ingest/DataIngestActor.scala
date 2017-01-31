package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.ingest.jira.JiraDataReaderActor
import de.codecentric.ccdashboard.service.timesheet.db.cassandra.CassandraWriter
import de.codecentric.ccdashboard.service.timesheet.messages.{Start, Stop}
import de.codecentric.ccdashboard.service.timesheet.util.StatusRequest
import io.getquill.CassandraContextConfig

import scala.concurrent.duration._

/**
  * Created by bjacobs on 12.07.16.
  */
class DataIngestActor(conf: Config, cassandraContextConfig: CassandraContextConfig) extends Actor with ActorLogging {

  import context._

  def receive = {
    case Start =>
      log.info("Data ingest actor starting...")

      // Spawn DataWriter Actor for current database
      log.info("Spawning data-writer")
      val dataWriterActor = context.system.actorOf(Props(new DataWriterActor(CassandraWriter)), "data-writer")

      // Spawn DataReader Actor for JIRA and provide ActorRef
      log.info("Spawning data-reader")
      val dataReaderActor = context.system.actorOf(Props(new JiraDataReaderActor(conf, dataWriterActor)), "data-reader")

      context.system.scheduler.scheduleOnce(3.seconds, dataReaderActor, Start)

      become(running(dataWriterActor, dataReaderActor), discardOld = false)
  }

  def running(dataWriterActor: ActorRef, dataReaderActor: ActorRef): Receive = {
    case s: StatusRequest =>
      dataReaderActor ! s
      dataWriterActor ! s

    case Stop =>
      context.stop(dataReaderActor)
      context.stop(dataWriterActor)
      log.info("Stopped child actors")
      unbecome

    case x => log.info(s"Received unknown message: $x")
  }
}
