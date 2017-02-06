package de.codecentric.ccdashboard.service.timesheet.util

import akka.actor.{Actor, ActorLogging, ActorRef}
import de.codecentric.ccdashboard.service.timesheet.util.StatusActor.{StatusNotification, StatusQuery}

import scala.collection.mutable

object StatusActor {
  case class StatusNotification(name: String, status: Map[String, String], importCompleted: Option[Boolean] = None)
  case object StatusQuery
}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class StatusActor extends Actor with ActorLogging {
  val statusMap = mutable.Map[String, Map[String, String]]()
  val importCompletedMap = mutable.Map[String, Boolean]()

  override def receive: Receive = {
    case StatusNotification(name, status, importCompleted) =>
      statusMap += (name -> status)
      for (b <- importCompleted) {
        importCompletedMap += (name -> b)
      }

    case StatusQuery =>
      val allImportsCompleted = importCompletedMap.values.forall(identity)
      sender() ! StatusQueryResponse(statusMap.toMap, allImportsCompleted)
  }
}




case class StatusQueryResponse(statusMap: Map[String, Map[String, String]], importCompleted: Boolean)
