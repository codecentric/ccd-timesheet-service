package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDateTime
import java.util.Date

import akka.actor.{Actor, ActorLogging, ActorRef}
import de.codecentric.ccdashboard.service.timesheet.data.ingest.DataWriterActor._
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import de.codecentric.ccdashboard.service.timesheet.util.StatusActor.StatusNotification

object DataWriterActor {

  case class UtilizationAggregation(username: String, payload: Map[Date, List[Option[Double]]])
  case class Teams(content: List[Team])
  case class TeamMemberships(teamId: Int, teamMembers: List[TeamMember])
  case class StatusRequest(statusActor: ActorRef)
  final case class Worklogs(content: List[Worklog])
  final case class Users(content: List[User])
}

/**
  * Actor that receives Worklogs from a DataIngestActor and stores inserts them into the database
  */
class DataWriterActor(dbWriter: DatabaseWriter) extends Actor with ActorLogging {

  var lastWrite: Option[LocalDateTime] = None

  def receive = {
    case Worklogs(worklogs) =>
      log.debug(s"Received ${worklogs.size} worklogs to store")

      dbWriter.insertWorklogs(worklogs)

      lastWrite = Some(LocalDateTime.now())

    case Users(users) =>
      log.debug(s"Received ${users.size} users to store")

      dbWriter.deleteUsers()
      dbWriter.insertUsers(users)

      lastWrite = Some(LocalDateTime.now())

    case i: Issue =>
      log.debug(s"Received one issue to store")

      dbWriter.insertIssue(i)

      lastWrite = Some(LocalDateTime.now())

    case Teams(teams) =>
      log.debug(s"Received ${teams.size} teams to store")

      dbWriter.deleteTeams()
      dbWriter.insertTeams(teams)

      lastWrite = Some(LocalDateTime.now())

    case TeamMemberships(teamId, members) =>
      log.debug(s"Received ${members.size} members for team $teamId to store")

      dbWriter.insertTeamMembers(members,teamId)

      lastWrite = Some(LocalDateTime.now())

    case UserSchedules(username, userSchedules) =>
      log.debug(s"Received ${userSchedules.size} user schedules for user $username")

      dbWriter.insertUserSchedules(userSchedules)

      lastWrite = Some(LocalDateTime.now())

    case UtilizationAggregation(username, payload) =>
      payload.foreach {
        case (date, values) =>
          val report = UserUtilization(username, date, values.head, values(1), values(2), values(3), values(4), values(5), values(6), values(7), values(8), values(9), values(10))
          dbWriter.insertUtilization(report)
      }

    case StatusRequest(statusActor) =>
      statusActor ! StatusNotification("DataWriter", Map(
        "status" -> "running",
        "last write" -> lastWrite.map(_.toString).getOrElse("")
      ))

  }
}
