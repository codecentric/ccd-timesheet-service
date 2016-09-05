package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{TeamMember, TeamMemberable}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class JiraTempoTeamMember(membership: Option[JiraTempoTeamMembership], member: JiraTempoTeamMemberUser) extends TeamMemberable {
  override def toTeamMember: TeamMember = TeamMember(member.name, membership.flatMap(_.dateFromANSI.flatten))
}

case class JiraTempoTeamMembership(dateFromANSI: Option[Option[Date]])

case class JiraTempoTeamMemberUser(name: String)