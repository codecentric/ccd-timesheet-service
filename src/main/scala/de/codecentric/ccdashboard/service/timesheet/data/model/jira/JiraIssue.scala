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
    val components = fields.components.map(c => c.id -> c.name).toMap

    val f1 = fields.customfield_10084.map(s => "customfield_10084" -> Map(s -> None))
    val f2 = fields.customfield_12300.map(f => "customfield_12300" -> Map(f.id -> Some(f.value)))
    val customFieldsMap = Seq(f1, f2).flatten.toMap

    Issue(id, key, self, fields.summary, components, customFieldsMap, JiraIssueFieldIssueType.unapply(fields.issuetype).toMap)
  }
}

case class JiraIssueFields(summary: Option[String],
                           components: Seq[JiraIssueFieldComponents],
                           customfield_12300: Option[JiraIssueFieldCustomField12300],
                           customfield_10084: Option[String],
                           issuetype: JiraIssueFieldIssueType)

case class JiraIssueFieldComponents(id: String, name: String)

case class JiraIssueFieldCustomField12300(value: String, id: String)

case class JiraIssueFieldIssueType(name: String, id: String)