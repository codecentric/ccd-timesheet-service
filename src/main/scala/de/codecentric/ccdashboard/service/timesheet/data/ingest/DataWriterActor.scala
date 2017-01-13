package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDateTime
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import de.codecentric.ccdashboard.service.timesheet.db.cassandra.CassandraWriter
import de.codecentric.ccdashboard.service.timesheet.util.{StatusNotification, StatusRequest}
import io.getquill.{CassandraContextConfig, CassandraSyncContext, SnakeCase}

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
      import scala.collection.JavaConverters._

      log.debug(s"Received ${members.size} members for team $teamId to store")
      // insert members here as map into table team
      // Cassandra doesn't allow null values in collections so if there is no
      // Date available, use the 'initial' Date (Jan. 1 1970)
      val membersMap = members.map {
        case TeamMember(name, date) => name -> date.getOrElse(new Date(0))
      }.toMap.asJava
      dbWriter.updateTeams(membersMap, teamId)

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

    case x => log.warning(s"Received unknown message: $x")
  }
}
