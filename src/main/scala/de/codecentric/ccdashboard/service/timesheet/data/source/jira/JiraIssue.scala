package de.codecentric.ccdashboard.service.timesheet.data.source.jira

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

case class JiraIssue(id: String, key: String, fields: JiraIssueFields)

case class JiraIssueFields(summary: Option[String],
                           components: Seq[JiraIssueFieldComponents],
                           customfield_12300: Option[JiraIssueFieldCustomField12300],
                           customfield_10084: Option[String],
                           issuetype: JiraIssueFieldIssueType)

case class JiraIssueFieldComponents(id: String, name: String)

case class JiraIssueFieldCustomField12300(value: String, id: String)

case class JiraIssueFieldIssueType(name: String, id: String)