package de.codecentric.ccdashboard.service.timesheet.data.model

import java.time.LocalDate

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class UserAvailabilities(content: List[UserAvailability]) extends UserAvailabilitiesable {
  override def toUserAvailabilities: UserAvailabilities = this
}

case class UserAvailability(dateFrom: LocalDate, dateTo: LocalDate, availability: Double, teamId: Int)

trait UserAvailabilitiesable {
  def toUserAvailabilities: UserAvailabilities
}