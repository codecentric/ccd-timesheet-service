package de.codecentric.ccdashboard.service.timesheet.data.ingest.jira

import java.time.LocalDate
import java.util.Date

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query
import de.codecentric.ccdashboard.service.timesheet.data.encoding._

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
trait JiraRequestUriGenerators {
  val c: JiraConfig

  def scheme: String

  def authority: Uri.Authority

  def getWorklogRequestUri(fromDate: LocalDate, toDate: LocalDate) = {
    val queryString = Query(Map(
      "dateFrom" -> fromDate.toString,
      "dateTo" -> toDate.toString,
      "format" -> "xml",
      "tempoApiToken" -> c.tempoApiToken,
      "projectKey" -> c.timesheetProjectKey)).toString

    val path = Uri.Path(c.jiraTempoWorklogsServicePath)

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getJiraUsersRequestUri(mailSuffix: String) = {
    val path = Uri.Path(c.jiraUsersServicePath)
    val queryString = Query(Map(
      "username" -> mailSuffix,
      "maxResults" -> "100000")).toString

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getJiraIssueDetailsUri(issueId: Either[String, Int]) = {
    val issueIdString = issueId match {
      case Left(key) => key
      case Right(id) => id.toString
    }
    val path = Uri.Path(c.jiraIssueDetailsServicePath) / issueIdString
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamsUri = {
    val path = Uri.Path(c.jiraTempoTeamServicePath)
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoTeamMembersUri(teamId: Int) = {
    val path = Uri.Path(c.jiraTempoTeamMembersServicePath.format(teamId.toString))
    Uri(scheme = scheme, authority = authority, path = path)
  }

  def getJiraTempoUserScheduleUri(username: String, from: Date, to: Date) = {
    val path = Uri.Path(c.jiraTempoUserScheduleServicePath)
    val queryString = Query(Map(
      "user" -> username,
      "from" -> dateIsoFormatter(from),
      "to" -> dateIsoFormatter(to)
    )).toString()

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

  def getTempoUserAvailabilityUri(username: String, from: Date, to: Date) = {
    val path = Uri.Path(c.jiraTempoUserAvailabilityServicePath.format(username))
    val queryString = Query(Map(
      "from" -> dateIsoFormatter(from),
      "to" -> dateIsoFormatter(to)
    )).toString()

    Uri(scheme = scheme, authority = authority, path = path, queryString = Some(queryString))
  }

}
