package de.codecentric.ccdashboard.service.timesheet.data.source.jira

import akka.actor.ActorDSL._
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.ingest.JiraDataReaderActor
import org.scalatest._

/**
  * Created by bjacobs on 18.07.16.
  */
class JiraWorklogTest extends FlatSpec {

  /*
    override def afterAll {
      TestKit.shutdownActorSystem(system)
    }
  */

  "The JIRA data reader actor " should "read data from JIRA" in {
    val conf = ConfigFactory.load()
    implicit val system = ActorSystem("TestSystem")

    val dummyActor = actor(new Act {
      become {
        case _ ⇒ println("hi")
      }
    })
    val dataReaderActor = system.actorOf(Props(new JiraDataReaderActor(conf, dummyActor)), "data-reader")
    println("ABC")

    dataReaderActor ! "test"
  }

  /*
      "send back messages unchanged" in {
        val echo = system.actorOf(TestActors.echoActorProps)
        echo ! "hello world"
        expectMsg("hello world")
      }
  */
}
