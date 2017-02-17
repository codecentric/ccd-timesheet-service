package de.codecentric.ccdashboard.service.timesheet.util

import java.time.LocalDate

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.ingest.jira.JiraDataReaderActor
import de.codecentric.ccdashboard.service.timesheet.data.ingest.jira.JiraDataReaderActor.{SingleJiraUserQueryTask, TempoUserScheduleQueryTask}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
object SingleJiraRequestExecutorApp extends App {
  private val conf = ConfigFactory.load()

  implicit val system = ActorSystem("request-actor-system")

  private val echoActor = system.actorOf(Props(new EchoActor))
  private val jiraReaderActor = system.actorOf(Props(new JiraDataReaderActor(conf, echoActor)))

  val user1 = "tanita.stache"
  val user2 = "florian.kraus"

  jiraReaderActor ! SingleJiraUserQueryTask(user1)
  jiraReaderActor ! SingleJiraUserQueryTask(user2)

  jiraReaderActor ! TempoUserScheduleQueryTask(user1, LocalDate.of(2016, 1, 1), LocalDate.of(2017, 12, 31))
  jiraReaderActor ! TempoUserScheduleQueryTask(user2, LocalDate.of(2016, 1, 1), LocalDate.of(2017, 12, 31))
}

class EchoActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case x => log.info(x.toString)
  }
}