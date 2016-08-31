package de.codecentric.ccdashboard.service.timesheet.data.ingest

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
  val worklogTableName = conf.getString("timesheet-service.tablenames.worklogs")

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
    case w: Worklogs =>
      val worklogs = w.content.toList
      log.info(s"Received ${worklogs.size} worklogs to store")
      ctx.run(insertWorklogs(worklogs))

    case u: Users =>
      val users = u.content.toList
      log.info(s"Received ${users.size} users to store")
      ctx.run(insertUsers(users))

    case i: Issue =>
      import scala.collection.JavaConverters._
      log.info(s"Received one issue to store")

      ctx.executeAction("INSERT INTO issue (id, issue_key, issue_url, summary, components, custom_fields, issue_type) VALUES(?, ?, ?, ?, ?, ?, ?)", (s) => {
        val componentsString = stringMapEncoder.f(i.components)
        val customFieldsString = stringMapMapEncoder.f(i.customFields)
        //val tupl = Metadata.

        //val tuple = TupleType.of(DataType.text(), DataType.text()).newValue(i.issuetype._1, i.issuetype._2)
        s.bind(i.id, i.issueKey, i.issueUrl, i.summary.getOrElse(""), i.components.asJava, i.customFields.asJava, i.issuetype.asJava)
      })

    case x => log.warning(s"Received unknown message: $x")
  }
}