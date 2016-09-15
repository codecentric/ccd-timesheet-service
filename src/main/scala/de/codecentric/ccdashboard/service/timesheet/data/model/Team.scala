package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class Team(id: Int, name: String, members: Option[Map[String, Option[Date]]] = None) extends Teamable {
  override def toTeam: Team = this
}

trait Teamable {
  def toTeam: Team
}

case class Teams(content: List[Team])
