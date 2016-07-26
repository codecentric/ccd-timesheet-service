package de.codecentric.ccdashboard.service.timesheet.data.marshalling.json

import de.codecentric.ccdashboard.service.timesheet.data.source.jira._
import spray.json.DefaultJsonProtocol

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

trait JiraIssueJsonProtocol extends DefaultJsonProtocol {
  implicit val jiraIssueFieldIssueTypeFormat = jsonFormat2(JiraIssueFieldIssueType)
  implicit val jiraIssueFieldCustomField123000Format = jsonFormat2(JiraIssueFieldCustomField12300)
  implicit val jiraIssueFieldComponentsFormat = jsonFormat2(JiraIssueFieldComponents)
  implicit val jiraIssueFieldsFormat = jsonFormat5(JiraIssueFields)
  implicit val jiraIssueFormat = jsonFormat3(JiraIssue)
}