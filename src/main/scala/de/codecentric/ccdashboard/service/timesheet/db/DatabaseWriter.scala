package de.codecentric.ccdashboard.service.timesheet.db
import java.util.{Date, Map}

import de.codecentric.ccdashboard.service.timesheet.data.model._

import scala.concurrent.Future


trait DatabaseWriter {

  def insertWorklogs(logs: List[Worklog]): Future[Unit]
  def insertUsers(users: List[User]): Future[Unit]

  def insertIssue(issue: Issue): Future[Unit]

  def insertTeams(teams: List[Team]): Future[Unit]

  def insertUtilization(util: UserUtilization): Future[Unit]

  def insertUserSchedules(schedules: List[UserSchedule]): Future[Unit]

  def deleteUsers(): Future[Unit]
  def deleteTeams(): Future[Unit]

  def updateTeams(members: Map[String, Date], teamId: Int): Future[Unit]
  def insertTeamMembers(members: List[TeamMember], teamId: Int): Future[Unit]
}
