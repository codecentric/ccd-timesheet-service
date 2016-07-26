package de.codecentric.ccdashboard.service.timesheet.data.marshalling.json

import spray.json.DefaultJsonProtocol

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

object MasterJsonProtocol extends DefaultJsonProtocol
  with WorklogJsonProtocol
  with UserJsonProtocol
  with OtherTempoJsonProtocol
  with JiraIssueJsonProtocol
