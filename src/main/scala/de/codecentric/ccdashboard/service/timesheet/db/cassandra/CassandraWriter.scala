package de.codecentric.ccdashboard.service.timesheet.db.cassandra


import java.util.{Date, Map}

import com.datastax.driver.core.SocketOptions
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import de.codecentric.ccdashboard.service.timesheet.util.CassandraContextConfigWithSocketOptions
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.concurrent.Future


object CassandraWriter extends DatabaseWriter {

  import scala.collection.JavaConverters._

  lazy val cassandraContextConfig: CassandraContextConfig = createCassandraContext()

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

  def insertTeams(ts: List[Team]): Future[Unit] = {
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

  def updateTeams(ms: Map[String, Date], teamId: Int): Future[Unit] = {
    ctx.executeAction("UPDATE team SET members = ? WHERE id = ?", (st) =>
      st.bind(ms, teamId.asInstanceOf[java.lang.Integer])
    )
  }


  private def createCassandraContext(): CassandraContextConfig = {
    val conf = ConfigFactory.load()
    val dbConfigKey = conf.getString("timesheet-service.database-config-key")
    val dbConfig = ConfigFactory.load().getConfig(dbConfigKey)
    val socketOptions = new SocketOptions().setConnectTimeoutMillis(60000).setReadTimeoutMillis(60000)
    new CassandraContextConfigWithSocketOptions(dbConfig, socketOptions)
  }
}
