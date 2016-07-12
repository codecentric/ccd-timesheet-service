package de.codecentric.ccdashboard.service.timesheet.dataimport

import akka.actor.Actor
import akka.event.Logging

/**
  * Created by bjacobs on 12.07.16.
  */
class DataImportActor extends Actor with DataImporter {
  val log = Logging(context.system, this)

  def receive = {
    case "Start" => log.info("Data importer started")
    case "Stop" => log.info("Data importer stopped")
  }
}

trait DataImporter {

}
