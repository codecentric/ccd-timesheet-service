package de.codecentric.ccdashboard.service.timesheet.db
import java.util.{Date, Map}

import de.codecentric.ccdashboard.service.timesheet.data.model._


trait DatabaseWriter {

  def insertWorklogs(logs: List[Worklog]): Unit
  def insertUsers(users: List[User]): Unit

  def insertIssue(issue: Issue): Unit

  def insertTeams(teamss: List[Team]): Unit

  def insertUtilization(util: UserUtilization): Unit

  def insertUserSchedules(schedules: List[UserSchedule]): Unit

  def deleteUsers(): Unit
  def deleteTeams(): Unit

  def updateTeams(members: Map[String, Date], teamId: Int): Unit
}
