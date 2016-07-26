package de.codecentric.ccdashboard.service.timesheet.data.marshalling.xml

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import de.codecentric.ccdashboard.service.timesheet.data.source.jira.JiraWorklog

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

object Unmarshallers {
  val jiraWorklogUnmarshaller = new FromEntityUnmarshaller[Seq[JiraWorklog]]() {
    override def apply(value: HttpEntity)(implicit ec: ExecutionContext, materializer: Materializer): Future[Seq[JiraWorklog]] = {
      Unmarshal(value).to[String].map(s => {
        val xml = XML.loadString(s)
        xml.child.map(JiraWorklog.fromXml)
      })
    }
  }
}
