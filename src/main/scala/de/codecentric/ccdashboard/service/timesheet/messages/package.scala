package de.codecentric.ccdashboard.service.timesheet

import java.time.LocalDate
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, Teams, User, Worklog}

/**
  * Collection of all message and query classes used in communication between the actors
  *
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
package object messages {

  /* Life-cycle messages */
  case class Start()

  case class Stop()

  /* Query messages */
  /**
    * Query for worklogs
    */
  case class WorklogQuery(username: String, from: Option[Date], to: Option[Date])

  /**
    * Query for worklogs response
    *
    * @param worklogs the worklogs returned by query
    */
  case class WorklogQueryResult(worklogs: Seq[Worklog])

  /**
    * Query for a user
    */
  case class UserQuery(username: String)

  /**
    * Query for user response
    */
  case class UserQueryResult(user: Option[User])

  /**
    * Query for an issue
    */
  case class IssueQuery(id: String)

  /**
    * Query for issue response
    */
  case class IssueQueryResult(issue: Option[Issue])

  /**
    * Query for all teams or one specific team
    */
  case class TeamQuery(teamId: Option[Int] = None)

  /**
    * Query for all teams or one specific response
    */
  case class TeamQueryResponse(teams: Option[Teams])



  /* TODO: describe tasks */

  case class TempoWorklogQueryTask(toDate: LocalDate, fromDate: LocalDate, syncing: Boolean)

  case class JiraUserQueryTask()

  case class JiraIssueDetailsQueryTask(issueId: Either[String, Int])

  case class JiraTempoTeamQueryTask()

  case class JiraTempoTeamMembersQueryTask(teamIdsToQuery: List[Int])

}
