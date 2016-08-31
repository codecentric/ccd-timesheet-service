package de.codecentric.ccdashboard.service.timesheet

import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{User, Worklog}

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

  /* TODO: describe tasks */

  case class TempoWorklogQueryTask()

  case class JiraUserQueryTask(iteration: Int, charIndex: Int)

  case class JiraIssueDetailsQueryTask(issueId: Either[String, Int])

  case class JiraTempoTeamQueryTask()

  case class JiraTempoTeamMembersQueryTask(teamId: Int)

}
