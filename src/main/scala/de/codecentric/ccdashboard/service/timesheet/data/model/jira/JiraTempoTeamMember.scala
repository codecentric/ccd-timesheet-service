package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import java.time.LocalDate

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class JiraTempoTeamMember(id: Int, membership: JiraTempoTeamMembership, member: JiraTempoTeamMemberUser)

case class JiraTempoTeamMembership(dateFromANSI: Option[LocalDate])

case class JiraTempoTeamMemberUser(name: String)