package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataAggregationActor.PerformUtilizationAggregation
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataWriterActor.UtilizationAggregation
import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, Worklog}
import de.codecentric.ccdashboard.service.timesheet.data.model.Issue._

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

object DataAggregationActor {
  case class PerformUtilizationAggregation(username: String, worklogs: List[Worklog], issues: List[Issue])
}

class DataAggregationActor(conf: Config, dataWriter: ActorRef) extends Actor with ActorLogging {

  import context.dispatcher

  def receive = {
    case PerformUtilizationAggregation(username, worklogs, issues) =>

      // Group worklogs by day and associate the relevant ticket to each worklog
      val dateGroupedWorklogs =
        worklogs
          .groupBy(_.workDate)
          .map {
            case (date, w) =>
              (date, w.map(worklog => (worklog, issues.find(_.issueKey == worklog.issueKey))))
          }

      val initValues: List[Option[Double]] = List.fill(Issue.Types.maxId) {
        None
      }

      // Aggregates all worklog hours of one day to one array of doubles to be stored in database
      val utilizationAggregationValues = dateGroupedWorklogs.map {
        case (date, list) =>
          val hoursSums = list.map {
            case (worklog, Some(issue)) =>
              val billable = issue.isBillable
              val issueType = issue.getIssueType
              (billable, issueType, worklog.hours)
          }.foldLeft(initValues) {
            case (current, (billable, issueType, hours)) =>
              val idx = if (billable) 0 else issueType.id

              val newValue = current(idx).map(_ + hours).orElse(Some(hours))
              current.updated(idx, newValue)
          }
          (date, hoursSums)
      }

      dataWriter ! UtilizationAggregation(username, utilizationAggregationValues)

    case _ => log.warning("Received unknown message")
  }
}


