package de.codecentric.ccdashboard.service.timesheet.data.model

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
final case class Issue(id: String,
                       issueKey: String,
                       issueUrl: String,
                       summary: Option[String],
                       components: Map[String, String],
                       customFields: Map[String, Map[String, String]],
                       issueType: Map[String, String]) extends Issueable {
  override def toIssue: Issue = this
}

trait Issueable {
  def toIssue: Issue
}

final case class Issues(content: Seq[Issue])