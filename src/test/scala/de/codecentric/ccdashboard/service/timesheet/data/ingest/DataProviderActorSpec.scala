package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate
import java.util.Date

import akka.actor.Props
import akka.testkit.TestActorRef
import de.codecentric.ccdashboard.service.timesheet.BaseAkkaSpec
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import de.codecentric.ccdashboard.service.timesheet.db.{DatabaseReader, DatabaseWriter}
import de.codecentric.ccdashboard.service.timesheet.messages.{ReportQueryAggregationType, UserQuery, UserReportQuery}
import org.scalamock.scalatest.MockFactory

class DataProviderActorSpec extends BaseAkkaSpec with MockFactory {

  "DataProviderActor" should {
    "handle user queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val name = "john.doe"

      actorRef ! UserQuery(name)

      (reader.getTeamMembership _).verify(name)
    }

    "handle user report queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val name = "john.doe"

      actorRef ! UserReportQuery(name, None, None, ReportQueryAggregationType.MONTHLY)

      (reader.getTeamMembership _).verify(name)
    }
  }
}

