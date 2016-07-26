package de.codecentric.ccdashboard.service.timesheet.data.marshalling.json

import de.codecentric.ccdashboard.service.timesheet.data.source.jira.{AvatarUrls, User}
import spray.json.DefaultJsonProtocol

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

trait UserJsonProtocol extends DefaultJsonProtocol {
  implicit val avatarUrlsFormat = jsonFormat4(AvatarUrls)
  implicit val userFormat = jsonFormat9(User)
}
