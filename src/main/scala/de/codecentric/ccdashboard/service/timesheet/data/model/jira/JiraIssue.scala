package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, Issueable}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

/**
  * @param id     Id of this issue
  * @param key    Key of this issue, e.g. "CCD-36" for issue 36 in project CCD
  * @param self   URL pointing to this issue in JIRA
  * @param fields Specific fields of this issue
  */

case class JiraIssue(id: String, key: String, self: String, fields: JiraIssueFields) extends Issueable {
  override def toIssue: Issue = {
    val component = fields.components.take(1).map(c => c.id -> c.name).toMap
    val dailyRate = fields.customfield_10084.flatMap(_.value)
    val invoicing = fields.customfield_12300.map(i => i.id -> i.value).toMap

    Issue(id, key, self, fields.summary, component, dailyRate, invoicing, JiraIssueFieldIssueType.unapply(fields.issuetype).toMap)
  }
}

case class JiraIssueFields(summary: Option[String],
                           components: Seq[JiraIssueFieldComponent],
                           customfield_12300: Option[JiraIssueFieldCustomField12300],
                           customfield_10084: Option[JiraIssueFieldCustomField10084],
                           issuetype: JiraIssueFieldIssueType)

case class JiraIssueFieldComponent(id: String, name: String)

case class JiraIssueFieldCustomField10084(value: Option[String])

case class JiraIssueFieldCustomField12300(value: String, id: String)

case class JiraIssueFieldIssueType(name: String, id: String)