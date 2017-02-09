package de.codecentric.ccdashboard.service.timesheet.data.access


import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

import akka.actor.{ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import de.codecentric.ccdashboard.service.timesheet.data.access.WorkScheduleProviderActor.WorkScheduleQuery
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseReader

import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class WorkScheduleProviderActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockFactory {

  val reader = mock[DatabaseReader]
  val actorRef = TestActorRef(Props(new WorkScheduleProviderActor(reader)))

  override def afterAll() ={
    actorRef ! PoisonPill
  }

  "WorkScheduleProviderActor" should {

    "accept work schedule queries" in {
      import de.codecentric.ccdashboard.service.timesheet.util.DateConversions._

      val year = 2017
      val yearStart = LocalDate.ofYearDay(year, 1)
      val yearEnd = yearStart.`with`(TemporalAdjusters.lastDayOfYear())
      val query = WorkScheduleQuery("john.doe", Option(2017))

      (reader.getUserSchedules _)
        .expects(query.username, yearStart.asUtilDate, yearEnd.asUtilDate)
        .returning(Future(List.empty))

      (reader.getUtilizationReport _)
        .expects(query.username, yearStart.asUtilDate, yearEnd.asUtilDate)
        .returning(Future(List.empty))

      (reader.getUserTeamMembershipDates _)
        .expects(query.username)
        .returning(Future(List.empty))

      actorRef ! query

    }
  }
}
