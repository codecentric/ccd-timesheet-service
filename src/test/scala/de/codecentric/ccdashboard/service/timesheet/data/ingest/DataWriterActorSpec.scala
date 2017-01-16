package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.Props
import akka.testkit.TestActorRef
import de.codecentric.ccdashboard.service.timesheet.BaseAkkaSpec
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import org.scalamock.scalatest.MockFactory

class DataWriterActorSpec extends BaseAkkaSpec with MockFactory {

  "DataWriterActor" should {
    "handle empty worklogs" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))

      actorRef ! Worklogs(List())

      (writer.insertWorklogs _).verify(List())
    }
  }
}
