package de.codecentric.ccdashboard.service.timesheet.data.source.jira

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import de.codecentric.ccdashboard.service.timesheet.data.model._

import scala.xml.Node

case class JiraWorklog(worklogId: Int,
                       issueId: Int,
                       issueKey: String,
                       hours: Double,
                       workDate: LocalDate,
                       workDateTime: LocalDateTime,
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
                       externalTimestamp: Option[LocalDateTime],
                       externalHours: Double,
                       externalResult: String,
                       customField10084: Option[Double],
                       customField10100: String,
                       customField10406: Option[Double],
                       customField10501: Option[LocalDateTime],
                       hashValue: String) extends Workloggable {

  override def toWorklog = Worklog(
    worklogId, Issue(issueId, issueKey), hours, workDate, workDateTime, username, staffId, Billing(billingKey, billingAttributes),
    Activity(activityId, activityName), workDescription, parentKey, reporterUserName, External(externalId, externalTimestamp,
      externalHours, externalResult), CustomFields(customField10084, customField10100, customField10406, customField10501), hashValue
  )
}

object JiraWorklog {
  val jiraDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.S]")

  def fromXml(n: Node): JiraWorklog = {
    new JiraWorklog(
      (n \ "worklog_id").text.toInt,
      (n \ "issue_id").text.toInt,
      (n \ "issue_key").text,
      (n \ "hours").text.toDouble,
      LocalDate.parse((n \ "work_date").text),
      LocalDateTime.parse((n \ "work_date_time").text, jiraDateTimeFormat),
      (n \ "username").text,
      (n \ "staff_id").text,
      (n \ "billing_key").text,
      (n \ "billing_attributes").text,
      (n \ "activity_id").text,
      (n \ "activity_name").text,
      (n \ "work_description").text,
      optString((n \ "parent_key").text),
      (n \ "reporter").text,
      (n \ "external_id").text,
      optDTF((n \ "external_tstamp").text),
      (n \ "external_hours").text.toDouble,
      (n \ "external_result").text,
      optDouble((n \ "customField_10084").text),
      (n \ "customField_10100").text,
      optDouble((n \ "customField_10406").text),
      optDTF((n \ "customField_10501").text),
      (n \ "hash_value").text
    )
  }

  def opt[T](f: String => T)(s: String): Option[T] = if (s.isEmpty) None else Some(f(s))

  def optString = opt[String](identity)(_)

  def optDouble = opt[Double](_.toDouble)(_)

  def optDTF = opt[LocalDateTime](LocalDateTime.parse(_, jiraDateTimeFormat))(_)
}
