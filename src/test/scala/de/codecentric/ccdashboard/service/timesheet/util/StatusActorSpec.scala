package de.codecentric.ccdashboard.service.timesheet.util

import akka.actor.Props
import akka.testkit.TestProbe
import de.codecentric.ccdashboard.service.timesheet.BaseAkkaSpec
import de.codecentric.ccdashboard.service.timesheet.util.StatusActor.{StatusNotification, StatusQuery}

class StatusActorSpec extends BaseAkkaSpec {

  "StatusActor" should {
    "handle StatusNotification and StatusQuery commands" in {
      val sender = TestProbe()
      implicit val senderRef = sender.ref

      val statusActor = system.actorOf(Props(new StatusActor))

      statusActor ! StatusQuery
      sender.expectMsg(StatusQueryResponse(Map.empty, importCompleted = true))

      val status1 = Map("key1" -> "value1")
      val status2 = Map("key2" -> "value2")
      statusActor ! StatusNotification("status1", status1)
      statusActor ! StatusNotification("status2", status2, Some(false))

      statusActor ! StatusQuery
      sender.expectMsg(StatusQueryResponse(Map("status1" -> status1, "status2" -> status2), importCompleted = false))
    }
  }
}
