package de.codecentric.ccdashboard.service.timesheet.data.access

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.datastax.driver.core.{Cluster, Session}
import com.typesafe.config.Config

import scala.concurrent.duration._

/**
  * Created by bjacobs on 18.07.16.
  */
class DatabaseInitializerActor(conf: Config) extends Actor with ActorLogging {
  val dbConfigKey = conf.getString("timesheet-service.database-config-key")
  val cassandraConfig = conf.getConfig(dbConfigKey)

  val contactPoint = cassandraConfig.getString("session.contactPoint")
  val keyspaceName = cassandraConfig.getString("keyspace")
  val username = cassandraConfig.getString("session.credentials.0")
  val password = cassandraConfig.getString("session.credentials.1")

  import context.dispatcher

  def receive: Receive = {
    case InitDatabaseConnection =>
      val requester = sender
      log.info("Received Database initialization request")
      self ! TryConnect(requester)

    case t@TryConnect(requester) =>
      try {
        val clusterBuilder = Cluster.builder()
        val cluster = clusterBuilder
          .addContactPoint(contactPoint)
          .withCredentials(username, password)
          .build()

        val session = cluster.connect()
        self ! TryInit(requester, session)
      } catch {
        case e: Exception =>
          log.warning("Error connecting to Cassandra database", e)
          log.info("Retrying in 10 seconds")
          context.system.scheduler.scheduleOnce(10.seconds, self, t)
      }

    case TryInit(requester, session) =>
      try {
        session.execute("DROP KEYSPACE IF EXISTS ccd_timesheet_test;")

        val createKeyspaceCommand = "CREATE KEYSPACE ccd_timesheet_test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"
        log.info(s"Executing SQL: $createKeyspaceCommand")
        val createKeyspaceCommandResult = session.execute(createKeyspaceCommand)

        val useKeyspaceCommand = "USE ccd_timesheet_test;"
        log.info(s"Executing SQL: $useKeyspaceCommand")
        session.execute(useKeyspaceCommand)

        val createTablesCommand1 =
          """CREATE TABLE worklog (
            |  worklog_id         INT,
            |  issue_id           INT,
            |  issue_key          TEXT,
            |  hours              DOUBLE,
            |  work_date          TIMESTAMP,
            |  work_date_time     TIMESTAMP,
            |  username           TEXT,
            |  staff_id           TEXT,
            |  billing_key        TEXT,
            |  billing_attributes TEXT,
            |  activity_id        TEXT,
            |  activity_name      TEXT,
            |  work_description   TEXT,
            |  parent_key         TEXT,
            |  reporter_user_name TEXT,
            |  external_id        TEXT,
            |  external_timestamp TIMESTAMP,
            |  external_hours     DOUBLE,
            |  external_result    TEXT,
            |  custom_field10084  DOUBLE,
            |  custom_field10100  TEXT,
            |  custom_field10406  DOUBLE,
            |  custom_field10501  TIMESTAMP,
            |  hash_value         TEXT,
            |  PRIMARY KEY (username, work_date)
            |);""".stripMargin
        val createTablesCommand2 =
          """
            |CREATE TABLE user (
            |  self          TEXT,
            |  userkey       TEXT,
            |  name          TEXT,
            |  email_address TEXT,
            |  avatar_url    TEXT,
            |  display_name  TEXT,
            |  active        BOOLEAN,
            |  time_zone     TEXT,
            |  locale        TEXT,
            |  PRIMARY KEY (userkey)
            |);""".stripMargin

        val createTablesCommand3 =
          """
            |CREATE TABLE issue (
            |  id            TEXT,
            |  issue_key     TEXT,
            |  issue_url     TEXT,
            |  summary       TEXT,
            |  components    MAP<TEXT, TEXT>,
            |  custom_fields FROZEN<MAP<TEXT, MAP<TEXT, TEXT>>>,
            |  issue_type    MAP<TEXT, TEXT>,
            |  PRIMARY KEY (id)
            |);""".stripMargin

        val createTablesCommand4 =
          """
            |CREATE TABLE team (
            |  id      INT,
            |  name    TEXT,
            |  members MAP<TEXT, TIMESTAMP>,
            |  PRIMARY KEY(id)
            |);""".stripMargin

        val createTablesCommand5 =
          """
            |CREATE INDEX ON team (KEYS(members));""".stripMargin

        log.info(
          "Executing CREATE TABLES...")

        List(createTablesCommand1, createTablesCommand2, createTablesCommand3, createTablesCommand4, createTablesCommand5).foreach(
          session.execute
        )
        session.close()
        requester ! InitDatabaseConnectionSuccess
      } catch {
        case e: Exception =>
          e.printStackTrace()
          log.error("Error when executing initialization SQL statement.", e)
          requester ! InitDatabaseConnectionFailure
      }
  }
}

case object InitDatabaseConnection

sealed trait InitDatabaseConnectionResponse

case object InitDatabaseConnectionSuccess extends InitDatabaseConnectionResponse

case object InitDatabaseConnectionFailure extends InitDatabaseConnectionResponse

private case class TryConnect(originalRequester: ActorRef)

private case class TryInit(originalRequester: ActorRef, session: Session)
