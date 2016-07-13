package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.Actor
import akka.event.Logging
import de.codecentric.ccdashboard.service.timesheet.dataimport.schema.DummyData
import de.codecentric.ccdashboard.service.timesheet.{Start, Stop}

// Use H2Driver to connect to an H2 database
import slick.driver.H2Driver.api._

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by bjacobs on 12.07.16.
  */
class DataImportActor extends Actor {
  val log = Logging(context.system, this)
  val db = Database.forConfig("h2mem1")
  val dummyTable = TableQuery[DummyData]

  def receive = {
    case Start => {
      // acquire connection to JIRA
      log.info("Data importer started")
      // self ! NextImport

      // Define a query
      val names = for (d <- dummyTable) yield d.name

      // Execute the query
      db.stream(names.result).foreach(name => log.info("Name: " + name))
    }

    case Stop => log.info("Data importer stopped")
  }
}