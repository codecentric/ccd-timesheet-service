package de.codecentric.ccdashboard.service.timesheet.data.model

import akka.http.scaladsl.model.headers.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class Team(id: Int, name: String, members: Option[Map[String, Date]] = None) extends Teamable {
  override def toTeam: Team = this
}

trait Teamable {
  def toTeam: Team
}

case class Teams(content: List[Team])
