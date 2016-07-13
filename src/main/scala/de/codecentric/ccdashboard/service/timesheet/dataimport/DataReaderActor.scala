package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.{Actor, ActorLogging, ActorRef}

/**
  * Created by bjacobs on 13.07.16.
  */
trait DataReaderActor extends Actor with ActorLogging

abstract class BaseDataReaderActor(val dataWriter: ActorRef) extends DataReaderActor

class JiraDataReaderActor(dataWriter: ActorRef) extends BaseDataReaderActor(dataWriter) {
  log.info("Instantiated")

  def receive = {
    case _ => log.info("Received message. Awesome.")
  }
}
