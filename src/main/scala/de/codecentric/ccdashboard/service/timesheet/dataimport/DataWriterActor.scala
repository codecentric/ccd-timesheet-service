package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.{Actor, ActorLogging}

/**
  * Created by bjacobs on 13.07.16.
  */
trait DataWriterActor extends Actor with ActorLogging

class H2DataWriterActor extends DataWriterActor {
  log.info("Instantiated")

  def receive = {
    case _ => log.info("Received message")
  }
}
