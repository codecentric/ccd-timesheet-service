package de.codecentric.ccdashboard.service.timesheet.data.ingest

import java.util.Date

import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.data.model._
import io.getquill.{CassandraSyncContext, SnakeCase}

/**
  * Actor that receives Worklogs from a DataIngestActor and stores inserts them into the database
  */
class DataWriterActor(conf: Config) extends Actor with ActorLogging {

  import de.codecentric.ccdashboard.service.timesheet.data.encoding._

  val dbConfigKey = conf.getString("timesheet-service.database-config-key")

  lazy val ctx = new CassandraSyncContext[SnakeCase](dbConfigKey)

  import ctx._

  def insertWorklogs(w: List[Worklog]) = quote {
    liftQuery(w).foreach(worklog => {
      // Until this is fixed, table name is hardcoded. See https://github.com/getquill/quill/issues/501
      // query[JiraWorklog].schema(_.entity(worklogTableName)).insert(worklog)
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

  def receive = {
    case Worklogs(worklogs) =>
      log.debug(s"Received ${worklogs.size} worklogs to store")
      ctx.run(insertWorklogs(worklogs))

    case Users(users) =>
      log.debug(s"Received ${users.size} users to store")
      ctx.run(insertUsers(users))

    case i: Issue =>
      import scala.collection.JavaConverters._
      log.debug(s"Received one issue to store")

      ctx.executeAction("INSERT INTO issue (id, issue_key, issue_url, summary, components, custom_fields, issue_type) VALUES(?, ?, ?, ?, ?, ?, ?)", (s) => {
        //val componentsString = stringMapEncoder.f(i.components)
        //val customFieldsString = stringMapMapEncoder.f(i.customFields)
        val customFieldsMap = i.customFields.map {
          case (k, v) => k -> v.asJava
        }.asJava

        s.bind(i.id, i.issueKey, i.issueUrl, i.summary.getOrElse(""), i.components.asJava, customFieldsMap, i.issueType.asJava)
      })

    case Teams(teams) =>
      log.debug(s"Received ${teams.size} teams to store")
      teams.foreach(team =>
        ctx.executeAction("INSERT INTO team (id, name) VALUES (?, ?) IF NOT EXISTS", (st) =>
          st.bind(team.id.asInstanceOf[java.lang.Integer], team.name)
        ))

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

    case x => log.warning(s"Received unknown message: $x")
  }
}