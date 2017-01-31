package de.codecentric.ccdashboard.service.timesheet.data.model

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class Team(id: Int, name: String) extends Teamable {
  override def toTeam: Team = this
}

trait Teamable {
  def toTeam: Team
}

case class Teams(content: List[Team])
