package de.codecentric.ccdashboard.service.timesheet

import akka.actor.{ActorIdentity, ActorPath, ActorRef, ActorSystem, Identify}
import akka.testkit.{ TestDuration, TestProbe }
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

abstract class BaseAkkaSpec extends WordSpec
  with Matchers
  with BeforeAndAfterAll {

  implicit class TestProbeOps(probe: TestProbe) {

    def expectActor(path: String)(implicit system: ActorSystem): ActorRef =
      expectActor(ActorPath.fromString(path))

    def expectActor(path: ActorPath)(implicit system: ActorSystem): ActorRef = {
      var actor = probe.system.deadLetters
      probe.awaitAssert {
        probe.system.actorSelection(path).tell(Identify(path), probe.ref)
        probe
          .expectMsgPF(100.milliseconds.dilated, s"actor under path $path") {
            case ActorIdentity(`path`, Some(a)) => actor = a
          }
      }
      actor
    }

    def expectNoActor(path: String)(implicit system: ActorSystem): Unit =
      expectNoActor(ActorPath.fromString(path))

    def expectNoActor(path: ActorPath)(implicit system: ActorSystem): Unit =
      probe.awaitAssert {
        probe.system.actorSelection(path).tell(Identify(path), probe.ref)
        probe.expectMsg(100.milliseconds.dilated, ActorIdentity(path, None))
      }
  }

  protected implicit val system = ActorSystem()

  override protected def afterAll() = {
    Await.ready(system.terminate(), 42.seconds)
    super.afterAll()
  }

}
