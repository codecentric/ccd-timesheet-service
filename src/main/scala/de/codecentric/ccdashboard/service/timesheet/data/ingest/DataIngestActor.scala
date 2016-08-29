package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.messages.{Start, Stop}

/**
  * Created by bjacobs on 12.07.16.
  */
class DataIngestActor(conf: Config) extends Actor with ActorLogging {

  import context._

  def receive = {
    case Start =>
      log.info("Data ingest actor starting...")

      // Spawn DataWriter Actor for current database
      val dataWriterActor = context.system.actorOf(Props(new DataWriterActor2(conf)), "data-writer")

      // Spawn DataReader Actor for JIRA and provide ActorRef
      val dataReaderActor = context.system.actorOf(Props(new JiraDataReaderActor(conf, dataWriterActor)), "data-reader")

      become(running(dataWriterActor, dataReaderActor), discardOld = false)

      dataReaderActor ! Start
  }

  def running(dataWriterActor: ActorRef, dataReaderActor: ActorRef): Receive = {
    case Stop =>
      context.stop(dataReaderActor)
      context.stop(dataWriterActor)
      log.info("Stopped child actors")
      unbecome

    case x => log.info(s"Received unknown message: $x")
  }
}