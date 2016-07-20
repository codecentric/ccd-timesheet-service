package de.codecentric.ccdashboard.service.timesheet.data.marshalling

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import de.codecentric.ccdashboard.service.timesheet.data.source.jira.{JiraWorklog, JiraWorklogs}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML

/**
  * Created by bjacobs on 20.07.16.
  */
object Unmarshallers {
  val jiraWorklogsUnmarshaller = new FromEntityUnmarshaller[JiraWorklogs]() {
    override def apply(value: HttpEntity)(implicit ec: ExecutionContext, materializer: Materializer): Future[JiraWorklogs] = {
      Unmarshal(value).to[String].map(s => {
        val xml = XML.loadString(s)
        val worklogs = xml \ "worklog"
        JiraWorklogs(worklogs.map(node => JiraWorklog.fromXml(node)))
      })
    }
  }
}
