package de.codecentric.ccdashboard.service.timesheet.util

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class StatusActor extends Actor with ActorLogging {
  val statusMap = mutable.Map[String, Map[String, String]]()

  override def receive: Receive = {
    case StatusNotification(name, status) =>
      statusMap += (name -> status)

    case StatusQuery =>
      sender() ! StatusQueryResponse(statusMap.toMap)
  }
}

case class StatusNotification(name: String, status: Map[String, String])

case class StatusRequest(statusActor: ActorRef)

case object StatusQuery

case class StatusQueryResponse(statusMap: Map[String, Map[String, String]])
