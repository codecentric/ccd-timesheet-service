package de.codecentric.ccdashboard.service.timesheet.data.marshalling.json

import de.codecentric.ccdashboard.service.timesheet.data.source.jira.JiraTempoTeam
import spray.json.DefaultJsonProtocol

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

trait OtherTempoJsonProtocol extends DefaultJsonProtocol {
  implicit val tempoTeamFormat = jsonFormat2(JiraTempoTeam)
}
