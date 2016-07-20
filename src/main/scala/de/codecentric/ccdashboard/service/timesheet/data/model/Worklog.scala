package de.codecentric.ccdashboard.service.timesheet.data.model

import java.time.{LocalDate, LocalDateTime}

import slick.lifted.Rep

/**
  * Created by bjacobs on 15.07.16.
  */
trait Workloggable {

  /**
    * Project concrete classes have to implement this function to map to the general Worklog class
    *
    * @return This instance as a Worklog instance
    */
  def toWorklog: Worklog
}

case class LiftedWorklog(worklogId: Rep[Int],
                         issue: Rep[Issue],
                         hours: Rep[Double],
                         workDate: Rep[LocalDate],
                         wordDateTime: Rep[LocalDateTime],
                         username: Rep[String],
                         staffId: Rep[String],
                         billing: Rep[Billing],
                         activity: Rep[Activity],
                         workDescription: Rep[String],
                         parentKey: Rep[Option[String]],
                         reporterUserName: Rep[String],
                         external: Rep[External],
                         customFields: Rep[CustomFields],
                         hashValue: Rep[String])

case class Worklog(worklogId: Int,
                   issue: Issue,
                   hours: Double,
                   workDate: LocalDate,
                   workDateTime: LocalDateTime,
                   username: String,
                   staffId: String,
                   billing: Billing,
                   activity: Activity,
                   workDescription: String,
                   parentKey: Option[String],
                   reporterUserName: String,
                   external: External,
                   customFields: CustomFields,
                   hashValue: String) extends Workloggable {

  override def toWorklog = this
}

case class Worklogs(get: Seq[Worklog])

case class Issue(id: Int, key: String)

case class Billing(id: String, attributes: String)

case class Activity(id: String, name: String)

case class External(id: String, timestamp: Option[LocalDateTime], hours: Double, result: String)

case class CustomFields(field10083: Option[Double], field10100: String, field10406: Option[Double], field10501: Option[LocalDateTime])


