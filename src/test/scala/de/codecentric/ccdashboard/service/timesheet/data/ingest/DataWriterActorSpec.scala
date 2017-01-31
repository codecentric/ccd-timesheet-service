package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.util.Date

import akka.actor.Props
import akka.testkit.TestActorRef
import de.codecentric.ccdashboard.service.timesheet.BaseAkkaSpec
import de.codecentric.ccdashboard.service.timesheet.data.model._
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

    "handle user messages" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val users = List()

      actorRef ! Users(users)

      (writer.deleteUsers _).verify()
      (writer.insertUsers _).verify(users)
    }

    "handle Issue messages" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val issue = Issue("","","",None, Map(), None, Map(), Map())

      actorRef ! issue

      (writer.insertIssue _).verify(issue)
    }

    "handle Team messages" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val teams = List()

      actorRef ! Teams(teams)

      (writer.deleteTeams _).verify()
      (writer.insertTeams _).verify(teams)
    }

    "handle Team membership messages" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val members = List()

      actorRef ! TeamMemberships(0, members)

      (writer.insertTeamMembers _).verify(members, 0)
    }

    "handle user schedules" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val schedules = List()

      actorRef ! UserSchedules("", schedules)

      (writer.insertUserSchedules _).verify(schedules)
    }

    "handle empty utilization aggregation" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val utilization = UserUtilization("", new Date(), None, None, None, None, None, None, None, None, None, None, None)

      actorRef ! UtilizationAggregation("", Map())

      (writer.insertUtilization _).when(utilization).never()
    }

    "handle Non-empty utilization aggregation" in {
      val writer = stub[DatabaseWriter]
      val actorRef = TestActorRef(Props(new DataWriterActor(writer)))
      val aggregation = UtilizationAggregation("", Map(new Date() -> List.range(0,11).map(n => Option(n.toDouble))))

      actorRef ! aggregation

      aggregation.payload.foreach {
        case (date, values) =>
          val report = UserUtilization(aggregation.username, date, values.head, values(1), values(2), values(3), values(4), values(5), values(6), values(7), values(8), values(9), values(10))
          (writer.insertUtilization _).verify(report)
      }
    }
  }
}
