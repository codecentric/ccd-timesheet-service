package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import de.codecentric.ccdashboard.service.timesheet.data.model.{Team, Teamable}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

case class JiraTempoTeam(id: Int, name: String) extends Teamable {
  override def toTeam: Team = Team(id, name)
}