package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.time.LocalDateTime
import java.util.Date

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.util.{StatusNotification, StatusRequest}
import io.getquill.{CassandraSyncContext, SnakeCase}

/**
  * Actor that receives Worklogs from a DataIngestActor and stores inserts them into the database
  */
class DataWriterActor(conf: Config) extends Actor with ActorLogging {

  import de.codecentric.ccdashboard.service.timesheet.data.encoding._

  val dbConfigKey = conf.getString("timesheet-service.database-config-key")

  lazy val ctx = new CassandraSyncContext[SnakeCase](dbConfigKey)

  import ctx._

  var lastWrite: Option[LocalDateTime] = None

  def insertWorklogs(w: List[Worklog]) = quote {
    liftQuery(w).foreach(worklog => {
      query[Worklog].insert(worklog)
    })
  }

  def insertUsers(w: List[User]) = quote {
    liftQuery(w).foreach(user => {
      query[User].insert(user)
    })
  }

  def insertIssues(i: List[Issue]) = quote {
    liftQuery(i).foreach(issue => {
      query[Issue].insert(issue)
    })
  }

  def insertUtilization(u: UserUtilization) = quote {
    query[UserUtilization].insert(lift(u))
  }

  def insertUserSchedules(u: List[UserSchedule]) = quote {
    liftQuery(u).foreach(userSchedule => {
      query[UserSchedule].insert(userSchedule)
    })
  }

  def receive = {
    case Worklogs(worklogs) =>
      log.debug(s"Received ${worklogs.size} worklogs to store")

      ctx.run(insertWorklogs(worklogs))

      lastWrite = Some(LocalDateTime.now())

    case Users(users) =>
      log.debug(s"Received ${users.size} users to store")

      ctx.run(query[User].delete)
      ctx.run(insertUsers(users))

      lastWrite = Some(LocalDateTime.now())

    case i: Issue =>
      import scala.collection.JavaConverters._
      log.debug(s"Received one issue to store")

      ctx.executeAction("INSERT INTO issue (id, issue_key, issue_url, summary, component, daily_rate, invoicing, issue_type) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", (s) => {
        s.bind(i.id, i.issueKey, i.issueUrl, i.summary.orNull, i.component.asJava, i.dailyRate.orNull, i.invoicing.asJava, i.issueType.asJava)
      })

      lastWrite = Some(LocalDateTime.now())

    case Teams(teams) =>
      log.debug(s"Received ${teams.size} teams to store")

      ctx.run(query[Team].delete)

      teams.foreach(team =>
        ctx.executeAction("INSERT INTO team (id, name) VALUES (?, ?) IF NOT EXISTS", (st) =>
          st.bind(team.id.asInstanceOf[java.lang.Integer], team.name)
        ))

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

      ctx.executeAction("UPDATE team SET members = ? WHERE id = ?", (st) =>
        st.bind(membersMap, teamId.asInstanceOf[java.lang.Integer])
      )
      lastWrite = Some(LocalDateTime.now())

    case UserSchedules(username, userSchedules) =>
      log.debug(s"Received ${userSchedules.size} user schedules for user $username")

      ctx.run(insertUserSchedules(userSchedules))

      lastWrite = Some(LocalDateTime.now())

    case UtilizationAggregation(username, payload) =>
      payload.foreach {
        case (date, values) =>
          val report = UserUtilization(username, date, values.head, values(1), values(2), values(3), values(4), values(5), values(6), values(7), values(8), values(9), values(10))
          ctx.run(insertUtilization(report))
      }

    case StatusRequest(statusActor) =>
      statusActor ! StatusNotification("DataWriter", Map(
        "status" -> "running",
        "last write" -> lastWrite.map(_.toString).getOrElse("")
      ))

    case x => log.warning(s"Received unknown message: $x")
  }
}