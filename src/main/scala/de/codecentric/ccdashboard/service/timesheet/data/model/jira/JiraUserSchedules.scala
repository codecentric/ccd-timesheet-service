package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{UserSchedule, UserSchedules, UserSchedulesable}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class JiraUserSchedules(days: List[JiraUserScheduleDay]) extends UserSchedulesable {
  override def toUserSchedules(username: String, availability: Double): UserSchedules = {
    val userScheduleList = days.map(d => UserSchedule(username, d.date, (d.requiredSeconds / 3600.0) * availability))
    UserSchedules(username, userScheduleList)
  }
}

case class JiraUserScheduleDay(date: Date, requiredSeconds: Int)