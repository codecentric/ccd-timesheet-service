package de.codecentric.ccdashboard.service.timesheet.db.cassandra


import java.util.Date

import com.datastax.driver.core._
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import de.codecentric.ccdashboard.service.timesheet.util.CassandraContextConfigWithOptions
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object CassandraWriter extends DatabaseWriter {

  import scala.collection.JavaConverters._

  lazy val cassandraContextConfig: CassandraContextConfig = createCassandraContext()

  lazy val metaData: Metadata = cassandraContextConfig.cluster.getMetadata()

  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig)

  import ctx._

  def insertWorklogs(ws: List[Worklog]): Future[Unit] = ctx.run(quote {
    liftQuery(ws).foreach(worklog => {
      query[Worklog].insert(worklog)
    })
  })

  def insertUsers(us: List[User]): Future[Unit] = ctx.run(quote {
    liftQuery(us).foreach(user => {
      query[User].insert(user)
    })
  })

  def insertIssue(i: Issue): Future[Unit] = {
    ctx.executeAction("INSERT INTO issue (id, issue_key, issue_url, summary, component, daily_rate, invoicing, issue_type) VALUES(?, ?, ?, ?, ?, ?, ?, ?)", (s) => {
      s.bind(i.id, i.issueKey, i.issueUrl, i.summary.orNull, i.component.asJava, i.dailyRate.orNull, i.invoicing.asJava, i.issueType.asJava)
    })
  }

  def insertTeams(ts: List[Team]): Future[Unit] = Future {
    ts.foreach(team =>
      ctx.executeAction("INSERT INTO team (id, name) VALUES (?, ?) IF NOT EXISTS", (st) =>
        st.bind(team.id.asInstanceOf[java.lang.Integer], team.name)
      )
    )
  }

  def insertUtilization(u: UserUtilization): Future[Unit] = ctx.run(quote {
    query[UserUtilization].insert(lift(u))
  })

  def insertUserSchedules(us: List[UserSchedule]): Future[Unit] = ctx.run(quote {
    liftQuery(us).foreach(userSchedule => {
      query[UserSchedule].insert(userSchedule)
    })
  })

  def deleteUsers(): Future[Unit] = ctx.run(quote {
    query[User].delete
  })

  def deleteTeams(): Future[Unit] = ctx.run(quote {
    query[Team].delete
  })

  def updateTeams(ms: java.util.Map[String, Date], teamId: Int): Future[Unit] = {
    ctx.executeAction("UPDATE team SET members = ? WHERE id = ?", (st) =>
      st.bind(ms, teamId.asInstanceOf[java.lang.Integer])
    )
  }

  def insertTeamMembers(members: List[TeamMember], teamId: Int): Future[Unit] = Future {
    members.foreach(member =>
      ctx.executeAction("INSERT INTO team_member (team_id, name, date_from, date_to, availability) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS", (st) =>
        st.bind(teamId.asInstanceOf[java.lang.Integer], member.name, member.dateFrom.orNull, member.dateTo.orNull, member.availability.getOrElse(100).asInstanceOf[java.lang.Integer])
      )
    )
  }

  private def createCassandraContext(): CassandraContextConfig = {
    val conf = ConfigFactory.load()
    val dbConfigKey = conf.getString("timesheet-service.database-config-key")
    val dbConfig = conf.getConfig(dbConfigKey)
    val socketOptions = new SocketOptions().setConnectTimeoutMillis(60000).setReadTimeoutMillis(60000)
    val poolingOptions = new PoolingOptions().setMaxQueueSize(Integer.MAX_VALUE)
    new CassandraContextConfigWithOptions(dbConfig, socketOptions = Some(socketOptions), poolingOptions = Some(poolingOptions))
  }
}


