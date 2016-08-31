package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
trait Workloggable {
  def toWorklog: Worklog
}

final case class Worklog(worklogId: Int,
                         issueId: Int,
                         issueKey: String,
                         hours: Double,
                         workDate: Date,
                         workDateTime: Date,
                         username: String,
                         staffId: String,
                         billingKey: String,
                         billingAttributes: String,
                         activityId: String,
                         activityName: String,
                         workDescription: String,
                         parentKey: Option[String],
                         reporterUserName: String,
                         externalId: String,
                         externalTimestamp: Option[Date],
                         externalHours: Double,
                         externalResult: String,
                         customField10084: Option[Double],
                         customField10100: String,
                         customField10406: Option[Double],
                         customField10501: Option[Date],
                         hashValue: String
                        ) extends Workloggable {
  override def toWorklog: Worklog = this
}

final case class Worklogs(content: Seq[Worklog])
