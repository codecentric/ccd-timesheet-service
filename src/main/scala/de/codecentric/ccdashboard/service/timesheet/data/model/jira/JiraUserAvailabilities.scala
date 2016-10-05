package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import java.time.LocalDate

import de.codecentric.ccdashboard.service.timesheet.data.model.{UserAvailabilities, UserAvailabilitiesable, UserAvailability}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class JiraUserAvailabilities(content: List[JiraUserAvailability]) extends UserAvailabilitiesable {
  override def toUserAvailabilities: UserAvailabilities = {
    UserAvailabilities(content.map(x => UserAvailability(x.dateFromANSI, x.dateToANSI, x.availability.toDouble / 100, x.teamId)))
  }
}

case class JiraUserAvailability(dateFromANSI: LocalDate, dateToANSI: LocalDate, availability: String, teamId: Int)
