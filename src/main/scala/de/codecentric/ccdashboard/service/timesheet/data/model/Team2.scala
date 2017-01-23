package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.jira.{JiraTempoTeam, JiraTempoTeamMember, JiraTempoTeamMembership}


case class Team2(id: Int, name: String, members: Map[String, TeamMemberInfo] = Map())

case class TeamMemberInfo(startDate: Option[Date],
                          endDate: Option[Date],
                          utilization: Double = 0,
                          availability: Int = 0,
                          daysWithoutBook: Int = 0)


object Team2 {
  def fromJiraTeam(team: JiraTempoTeam, members: Seq[JiraTempoTeamMember]): Team2 = {
    Team2(team.id, team.name, members.map(memberToTuple).toMap)
  }

  private def memberToTuple(member: JiraTempoTeamMember): (String, TeamMemberInfo) = {
    (member.member.name, member.membership.map(memeberShipToInfo).getOrElse(TeamMemberInfo(None, None)))
  }

  private def memeberShipToInfo(membership: JiraTempoTeamMembership) = {
    TeamMemberInfo(membership.dateFromANSI.flatten, membership.dateToANSI.flatten)
  }
}