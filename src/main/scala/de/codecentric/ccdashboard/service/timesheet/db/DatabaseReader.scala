package de.codecentric.ccdashboard.service.timesheet.db

import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.messages.{EmployeesQueryResponse, SingleTeamMembershipQueryResponse, TeamMembershipQueryResult, WorklogQueryResult}

import scala.concurrent.Future

trait DatabaseReader {
  def getTeamById(id: Int): Future[Team]

  def getTeams(): Future[List[Team]]

  def getIssueById(id: String): Future[Issue]
  def getUserSchedules(username: String, from: Date, to: Date): Future[List[UserSchedule]]

  def getWorklog(username: String, from: Option[Date], to: Option[Date]): Future[WorklogQueryResult]
  def getUtilizationReport(username: String, from: Date, to: Date): Future[List[UserUtilization]]
  def getUserSchedule(username: String, from: Date, to: Date): Future[List[UserSchedule]]

  def getTeamMembership(username: String): Future[List[TeamMembershipQueryResult]]
  def getEmployees(): Future[EmployeesQueryResponse]
  def getTeamIds(): Future[List[Int]]
  def getTeamMembers(teamId: Int): Future[SingleTeamMembershipQueryResponse]
  def getUserByName(username: String): Future[Option[User]]
}
