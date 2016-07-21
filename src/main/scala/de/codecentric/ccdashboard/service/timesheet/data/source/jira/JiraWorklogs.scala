package de.codecentric.ccdashboard.service.timesheet.data.source.jira

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML

/**
  * Created by bjacobs on 14.07.16.
  */

case class JiraWorklogs(jiraWorklogs: Seq[JiraWorklog])

object JiraWorklogs {
  type JiraWorklogsUnmarshaller = FromEntityUnmarshaller[JiraWorklogs]
  implicit val um = new JiraWorklogsUnmarshaller() {
    override def apply(value: HttpEntity)(implicit ec: ExecutionContext, materializer: Materializer): Future[JiraWorklogs] = {
      import akka.http.scaladsl.unmarshalling._
      Unmarshal(value).to[String].map(s => {
        val xml = XML.loadString(s)
        val worklogs = xml \ "worklog"
        JiraWorklogs(worklogs.map(node => JiraWorklog.fromXml(node)))
      })
    }
  }
}