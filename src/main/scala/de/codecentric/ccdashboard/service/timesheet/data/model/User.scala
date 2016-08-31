package de.codecentric.ccdashboard.service.timesheet.data.model

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
final case class User(self: String,
                      userkey: String,
                      name: String,
                      emailAddress: String,
                      avatarUrl: String,
                      displayName: String,
                      active: Boolean,
                      timeZone: String,
                      locale: String
                     ) extends Userable {
  override def toUser: User = this
}

trait Userable {
  def toUser: User
}

final case class Users(content: Seq[User])
