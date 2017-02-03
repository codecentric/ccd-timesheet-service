package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate
import java.util.Date

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, Team, TeamMember}
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader
import de.codecentric.ccdashboard.service.timesheet.messages._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DataProviderActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockFactory {

  //TODO setup actorRef only once
  "DataProviderActor" should {

    "handle worklog queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))
      val query = WorklogQuery("john.doe", None, None)

      (reader.getWorklog _)
        .expects(query.username, query.from, query.to)
        .returning(Future(WorklogQueryResult(Seq.empty)))

      actorRef ! query
    }

    "handle user queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val name = "john.doe"

      (reader.getUserTeamMembershipDates _).expects(name).returning(Future(List.empty))

      actorRef ! UserQuery(name)
    }

    "handle user report queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val name = "john.doe"

      (reader.getUserTeamMembershipDates _).expects(name).returning(Future(List.empty))

      actorRef ! UserReportQuery(name, None, None, None, ReportQueryAggregationType.MONTHLY)
    }

    "handle issue queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val id = "101"

      val mockIssue = Issue("test", "testKey", "testUrl", None, Map.empty, None, Map.empty, Map.empty)

      (reader.getIssueById _).expects(id).returning(Future(mockIssue))

      actorRef ! IssueQuery(id)
    }

    "handle single team membership queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      val teamId = 101

      (reader.getTeamMembers _).expects(teamId).returning(Future(List.empty))

      actorRef ! SingleTeamMembershipQuery(teamId)
    }

    "handle all team memberships queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      (reader.getTeamIds _).expects().returning(Future(List.empty))

      actorRef ! AllTeamMembershipQuery
    }

    "handle employee queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))

      (reader.getEmployees _).expects().returning(Future(EmployeesQueryResponse(List.empty)))

      actorRef ! EmployeesQuery
    }

    "handle team report queries" in {
      val reader = mock[DatabaseReader]
      val actorRef = TestActorRef(Props(new DataProviderActor(LocalDate.now(), reader)))
      val teamId = 101

      (reader.getTeamMembers _).expects(teamId).returning(Future(List(TeamMember(101, "user1", None, None, Some(100)))))
      (reader.getUserTeamMembershipDates _).expects("user1").returning(Future(List(TeamMember(42, "user1", None, None, None))))

      actorRef ! TeamReportQuery(teamId, None, None, ReportQueryAggregationType.MONTHLY)
    }
  }
}

