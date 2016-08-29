package de.codecentric.ccdashboard.service.timesheet

import de.codecentric.ccdashboard.service.timesheet.data.model.Worklog

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
    *
    * @param x Number of results to return
    */
  case class WorklogQuery(x: Int)

  /**
    * Query for worklogs response
    *
    * @param worklogs the worklogs returned by query
    */
  case class WorklogQueryResult(worklogs: Seq[Worklog])

  /* TODO: describe tasks */

  case class TempoQueryTask()

  case class JiraUserQueryTask(iteration: Int, charIndex: Int)

  case class JiraIssueDetailsQueryTask(issueId: Either[String, Int])

  case class JiraTempoTeamQueryTask()

  case class JiraTempoTeamMembersQueryTask(teamId: Int)

}
