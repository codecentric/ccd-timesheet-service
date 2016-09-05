package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class TeamMember(name: String, dateFrom: Option[Date]) extends TeamMemberable {
  override def toTeamMember: TeamMember = this
}

trait TeamMemberable {
  def toTeamMember: TeamMember
}

case class TeamMemberships(teamId: Int, teamMembers: List[TeamMember])
