package de.codecentric.ccdashboard.service.timesheet.data.source.jira

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

case class User(self: String, key: String, name: String, emailAddress: String, avatarUrls: AvatarUrls, displayName: String, active: Boolean, timeZone: String, locale: String)

case class AvatarUrls(`16x16`: String, `24x24`: String, `32x32`: String, `48x48`: String)