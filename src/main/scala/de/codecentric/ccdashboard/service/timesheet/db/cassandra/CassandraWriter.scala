package de.codecentric.ccdashboard.service.timesheet.db.cassandra


import java.util.Date

import com.datastax.driver.core._
import com.typesafe.config.ConfigFactory
import de.codecentric.ccdashboard.service.timesheet.data.model._
import de.codecentric.ccdashboard.service.timesheet.db.DatabaseWriter
import de.codecentric.ccdashboard.service.timesheet.util.CassandraContextConfigWithSocketOptions
import io.getquill.context.cassandra.CassandraSessionContext
import io.getquill.{CassandraAsyncContext, CassandraContextConfig, SnakeCase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.collection.immutable.Map


object CassandraWriter extends DatabaseWriter {

  import scala.collection.JavaConverters._

  lazy val cassandraContextConfig: CassandraContextConfig = createCassandraContext()

  lazy val metaData: Metadata = cassandraContextConfig.cluster.getMetadata()

  lazy val ctx = new CassandraAsyncContext[SnakeCase](cassandraContextConfig) with Encoders with Decoders

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
    val futures = ts.map(team =>
      ctx.executeAction("INSERT INTO team (id, name) VALUES (?, ?) IF NOT EXISTS", (st) =>
        st.bind(team.id.asInstanceOf[java.lang.Integer], team.name)
      )
    )

    Future.reduce(futures)((_,_) => ())
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

  def updateTeamWithMembers(ms: Map[String, TeamMemberInfo], teamId: Int): Future[Unit] = ctx.run(quote {
    query[Team2].filter(_.id == lift(teamId)).update(_.members -> lift(ms))
  })

  def insertTeamMembers(members: List[TeamMember], teamId: Int): Future[Unit] = {
    val futures = members.map(member =>
      ctx.executeAction("INSERT INTO team_member (teamId, memberName, dateFrom, dateTo, availability) VALUES (?, ?, ?, ?, ?) IF NOT EXISTS", (st) =>
        st.bind(teamId.asInstanceOf[java.lang.Integer], member.name, member.dateFrom.orNull, member.dateTo.orNull, member.availability.getOrElse(100).asInstanceOf[java.lang.Integer])
      )
    )

    Future.reduce(futures)((_,_) => ())
  }


  private def createCassandraContext(): CassandraContextConfig = {
    val conf = ConfigFactory.load()
    val dbConfigKey = conf.getString("timesheet-service.database-config-key")
    val dbConfig = conf.getConfig(dbConfigKey)
    val socketOptions = new SocketOptions().setConnectTimeoutMillis(60000).setReadTimeoutMillis(60000)
    new CassandraContextConfigWithSocketOptions(dbConfig, socketOptions)
  }


}

trait Encoders {
  this: CassandraSessionContext[_] =>

  import scala.collection.JavaConverters._

  implicit def setEncoder: Encoder[Map[String, TeamMemberInfo]] =
    encoder((index, value, row) => row.setMap(index, value.mapValues {
      case TeamMemberInfo(startDate, endDate, availability, _, _) => (startDate.orNull, endDate.orNull, availability)
    }.mapValues(value => {
      tupleType.newValue(value._1, value._2, value._3.asInstanceOf[java.lang.Double])
    }).asJava))

  lazy val tupleType = CassandraWriter.metaData.newTupleType(DataType.date(), DataType.date(), DataType.cint())
}

trait Decoders {
  this: CassandraSessionContext[_] =>

  import scala.collection.JavaConverters._

  implicit def setDecoder[T](implicit t: ClassTag[T]): Decoder[Map[String, T]] =
    decoder((index, row) => row.getMap(index, classOf[String], t.runtimeClass.asInstanceOf[Class[T]]).asScala.toMap)
}
