package de.codecentric.ccdashboard.service.timesheet.data.model.jira

import de.codecentric.ccdashboard.service.timesheet.data.model.{User, Userable}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

case class JiraUser(self: String,
                    key: String,
                    name: String,
                    emailAddress: String,
                    avatarUrls: JiraAvatarUrls,
                    displayName: String,
                    active: Boolean,
                    timeZone: String,
                    locale: String
                   ) extends Userable {
  override def toUser: User = User(self, key, name, emailAddress, avatarUrls.`48x48`, displayName, active, timeZone, locale)
}

case class JiraAvatarUrls(`16x16`: String, `24x24`: String, `32x32`: String, `48x48`: String)