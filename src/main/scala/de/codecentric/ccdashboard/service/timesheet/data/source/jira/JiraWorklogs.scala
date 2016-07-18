package de.codecentric.ccdashboard.service.timesheet.data.source.jira

import com.typesafe.scalalogging.StrictLogging
import spray.http.MediaTypes
import spray.httpx.unmarshalling.Unmarshaller

import scala.xml.NodeSeq

/**
  * Created by bjacobs on 14.07.16.
  */
case class JiraWorklogs(jiraWorklogs: Seq[JiraWorklog])

object JiraWorklogs extends StrictLogging {
  implicit val JiraWorklogsUnmarshaller: Unmarshaller[JiraWorklogs] = Unmarshaller.delegate[NodeSeq, JiraWorklogs](MediaTypes.`text/xml`, MediaTypes.`application/xml`) {
    nodeSeq =>
      val worklogs = nodeSeq \ "worklog"
      JiraWorklogs(worklogs.map(node => JiraWorklog.fromXml(node)))
  }
}