package de.codecentric.ccdashboard.service.timesheet.db
import de.codecentric.ccdashboard.service.timesheet.data.model._

trait DatabaseWriter {
  def insertWorklogs(logs: List[Worklog]): Unit

  def insertUsers(users: List[User]): Unit

  def insertIssue(issue: Issue): Unit

  def insertTeams(teams: List[Team]): Unit

  def insertUtilization(util: UserUtilization): Unit

  def insertUserSchedules(schedules: List[UserSchedule]): Unit

  def deleteUsers(): Unit

  def deleteTeams(): Unit

  def insertTeamMembers(members: List[TeamMember], teamId: Int): Unit
}
