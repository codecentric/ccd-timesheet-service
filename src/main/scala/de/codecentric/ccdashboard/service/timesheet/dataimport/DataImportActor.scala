package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.Actor
import akka.event.Logging
import de.codecentric.ccdashboard.service.timesheet.{Start, Stop}

/**
  * Created by bjacobs on 12.07.16.
  */
class DataImportActor extends Actor with DataImporter {
  val log = Logging(context.system, this)

  def receive = {
    case Start => {
      // acquire connection to JIRA
      log.info("Data importer started")
      // self ! NextImport
    }
    case Stop => log.info("Data importer stopped")
  }
}

trait DataImporter {

}
