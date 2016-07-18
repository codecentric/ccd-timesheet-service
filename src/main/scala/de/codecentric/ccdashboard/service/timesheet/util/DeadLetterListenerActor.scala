package de.codecentric.ccdashboard.service.timesheet.util

import akka.actor.{Actor, ActorLogging, DeadLetter}

class DeadLetterListenerActor extends Actor with ActorLogging {
  def receive = {
    case d: DeadLetter => log.warning(d.toString)
  }
}