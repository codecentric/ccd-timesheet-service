package de.codecentric.ccdashboard.service.timesheet.data.access

import java.sql.{Date, Timestamp}
import java.time.{LocalDate, LocalDateTime}

import de.codecentric.ccdashboard.service.timesheet.data.schema._
import slick.driver.JdbcProfile

import scala.language.higherKinds


class WorklogDAO(val driver: JdbcProfile) {
  // Import the Scala API from the driver
  import driver.api._

  implicit val myDateColumnType = MappedColumnType.base[LocalDate, Date](
    ld => Date.valueOf(ld),
    d => d.toLocalDate
  )

  implicit val timeColumnType = MappedColumnType.base[LocalDateTime, Timestamp](
    localDateTime => Timestamp.valueOf(localDateTime),
    timestamp => timestamp.toLocalDateTime
  )

  class WorklogTableRow(tag: Tag) extends Table[Worklog](tag, "worklogs") {
    def worklogId = column[Int]("worklog_id", O.PrimaryKey)

    def issueId = column[Int]("issue_id")

    def issueKey = column[String]("issue_key")

    def hours = column[Double]("hours")

    def workDate = column[LocalDate]("work_date")

    def workDateTime = column[LocalDateTime]("work_date_time")

    def username = column[String]("username")

    def staffId = column[String]("staff_id")

    def billingKey = column[String]("billing_key")

    def billingAttributes = column[String]("billing_attributes")

    def activityId = column[String]("activity_id")

    def activityName = column[String]("activity_name")

    def workDescription = column[String]("work_description")

    def parentKey = column[Option[String]]("parent_key")

    def reporterUserName = column[String]("reporter")

    def externalId = column[String]("external_id")

    def externalTimestamp = column[Option[LocalDateTime]]("external_tstamp")

    def externalHours = column[Double]("external_hours")

    def externalResult = column[String]("external_result")

    def customField10084 = column[Option[Double]]("customField_10084")

    def customField10100 = column[String]("customField_10100")

    def customField10406 = column[Option[Double]]("customField_10406")

    def customField10501 = column[Option[LocalDateTime]]("customField_10501")

    def hashValue = column[String]("hash_value")

    override def * = (worklogId, (issueId, issueKey), hours, workDate, workDateTime, username, staffId, (billingKey, billingAttributes),
      (activityId, activityName), workDescription, parentKey, reporterUserName, (externalId, externalTimestamp, externalHours, externalResult),
      (customField10084, customField10100, customField10406, customField10501), hashValue).shaped <> ( {
      case (worklogId, issue, hours, workDate, workDateTime, username, staffId, billing, activity, workDescription, parentKey, reporterUserName,
      external, customFields, hashValue) =>
        Worklog(worklogId, Issue.tupled.apply(issue), hours, workDate, workDateTime, username, staffId, Billing.tupled.apply(billing),
          Activity.tupled.apply(activity), workDescription, parentKey, reporterUserName, External.tupled.apply(external),
          CustomFields.tupled.apply(customFields), hashValue)
    }, { w: Worklog =>
      Some(w.worklogId, Issue.unapply(w.issue).get, w.hours, w.workDate, w.workDateTime, w.username, w.staffId, Billing.unapply(w.billing).get,
        Activity.unapply(w.activity).get, w.workDescription, w.parentKey, w.reporterUserName, External.unapply(w.external).get,
        CustomFields.unapply(w.customFields).get, w.hashValue)
    })
  }

  val props = TableQuery[WorklogTableRow]

  /** Create the database schema */
  def create: DBIO[Unit] = props.schema.create

  /** Insert a key/value pair */
  def insert(v: Worklog): DBIO[Int] = props += v

  def insert(v: Seq[Worklog]): DBIO[Option[Int]] = props ++= v

  /** Get the value for the given key */
  //  def get(k: String): DBIO[Option[String]] = (for (p <- props if p.key === k) yield p.value).result.headOption

  def getFirst(x: Int): DBIO[Seq[Worklog]] = props.take(x).result

  /** Get the first element for a Query from this DAO */
  def getFirst[M, U, C[_]](q: Query[M, U, C]): DBIO[U] = q.result.head
}