package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.codecentric.ccdashboard.service.timesheet.Start

// Use H2Driver to connect to an H2 database
// import slick.driver.H2Driver.api._

/**
  * Created by bjacobs on 12.07.16.
  */
class DataImportActor extends Actor with ActorLogging {

  import context._

  /*
    val db = Database.forConfig("h2mem1")
    val dummyTable = TableQuery[DummyData]
  */

  def receive = {
    case Start => {
      // acquire connection to JIRA
      log.info("Data importer starting...")

      // Spawn DataWriter Actor for current database
      val dataWriterActor = context.system.actorOf(Props[H2DataWriterActor], "data-writer")

      // Spawn DataReader Actor for JIRA and provide ActorRef
      val dataReaderActor = context.system.actorOf(Props(new JiraDataReaderActor(dataWriterActor)), "data-reader")

      become(running(dataWriterActor, dataReaderActor), discardOld = false)
    }


    // Define a query
    // val names = for (d <- dummyTable) yield d.name

    // Execute the query
    // db.stream(names.result).foreach(name => log.info("Name: " + name))
  }

  def running(dataWriterActor: ActorRef, dataReaderActor: ActorRef): Receive = {
    case "say" => {
      dataWriterActor ! "test"
      dataReaderActor ! "test"
    }
    case x => log.info("Some message. Thx: " + x)
  }
}