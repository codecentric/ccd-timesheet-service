package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDate

import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.access.DataProviderActor
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataAggregationActor.PerformUtilizationAggregation
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataWriterActor.UtilizationAggregation
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class DataAggregationActorSpec extends TestKit(ActorSystem("MySpec")) with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with MockFactory {

  val writer = mock[DatabaseWriter]
  val dataWriter = TestActorRef(Props(new DataWriterActor(writer)))
  val conf = mock[Config]
  val actorRef = TestActorRef(Props(new DataAggregationActor(conf, dataWriter)))

  override def afterAll() = {
    actorRef ! PoisonPill
  }


  "DataAggregationActor" should {

    "handle empty PerformUtilizationAggregation messages" in {

      val probe = TestProbe()

      probe.watch(dataWriter)

      actorRef ! PerformUtilizationAggregation("john.doe", List.empty, List.empty)

      probe.expectNoMsg()

    }
  }

}
