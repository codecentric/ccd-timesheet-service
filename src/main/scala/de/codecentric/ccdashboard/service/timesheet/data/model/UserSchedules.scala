package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class UserSchedules(username: String, userSchedules: List[UserSchedule]) extends UserSchedulesable {
  override def toUserSchedules(username: String): UserSchedules = this
}

case class UserSchedule(username: String, workDate: Date, requiredHours: Double)

trait UserSchedulesable {
  def toUserSchedules(username: String): UserSchedules
}
