package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate
import java.util.Date

import akka.actor.Props
import akka.testkit.TestActorRef
import de.codecentric.ccdashboard.service.timesheet.BaseAkkaSpec
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.model.Worklogs
import de.codecentric.ccdashboard.service.timesheet.db.{DatabaseReader, DatabaseWriter}
import de.codecentric.ccdashboard.service.timesheet.messages._
import org.scalamock.scalatest.MockFactory

class DataProviderActorSpec extends BaseAkkaSpec with MockFactory {

  //TODO setup actorRef only once
  "DataProviderActor" should {

    "handle worklog queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))
      val query = WorklogQuery("john.doe", None, None)

      actorRef ! query

      (reader.getWorklog _).verify(query.username, query.from, query.to)
    }

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

    "handle issue queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val id = "101"

      actorRef ! IssueQuery(id)

      (reader.getIssueById _).verify(id)
    }

    "handle single team membership queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val teamId = 101

      actorRef ! SingleTeamMembershipQuery(teamId)

      (reader.getTeamMembers _).verify(teamId)
    }

    "handle all team memberships queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      actorRef ! AllTeamMembershipQuery

      (reader.getTeamIds _).verify()
    }

    "handle employee queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      actorRef ! EmployeesQuery

      (reader.getEmployees _).verify()
    }

    "handle team report queries" in {
      val reader = stub[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))
      val teamId = 101

      actorRef ! TeamReportQuery(teamId, None, None, ReportQueryAggregationType.MONTHLY)

      (reader.getTeamById _).verify(teamId)
    }


  }
}

