package de.codecentric.ccdashboard.service.timesheet.routing

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
object CustomPathMatchers {
  val usernameMatcher = """[A-Za-z\.\-]+""".r
  val issueIdMatcher = """[A-Za-z0-9]+""".r
}
