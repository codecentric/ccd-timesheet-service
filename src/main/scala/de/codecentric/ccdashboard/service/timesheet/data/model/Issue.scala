package de.codecentric.ccdashboard.service.timesheet.data.model

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
final case class Issue(id: String,
                       issueKey: String,
                       issueUrl: String,
                       summary: Option[String],
                       component: Map[String, String],
                       dailyRate: Option[String],
                       invoicing: Map[String, String],
                       issueType: Map[String, String]) extends Issueable {
  override def toIssue: Issue = this
}

trait Issueable {
  def toIssue: Issue
}

final case class Issues(content: Seq[Issue])

object Issue {

  implicit class IssueFunctions(issue: Issue) {
    implicit def isBillable: Boolean = {
      val componentString = issue.component.headOption.map(_._2)
      val invoicingString = issue.invoicing.headOption.map(_._2)

      (componentString, invoicingString) match {
        case (Some("codecentric"), Some("Support")) => true
        case (Some("codecentric"), _) => false
        case (_, Some("Nicht abrechenbar")) => false
        case _ => true
      }
    }

    implicit def getIssueType: Issue.Types.Value = {
      issue.issueKey match {
        case "TS-1" => Issue.Types.ADMIN
        case "TS-2" => Issue.Types.VACATION
        case "TS-3" => Issue.Types.PRESALES
        case "TS-4" => Issue.Types.RECRUITING
        case "TS-5" => Issue.Types.ILLNESS
        case "TS-6" => Issue.Types.TRAVELTIME
        case "TS-7" => Issue.Types.TWENTYPERCENT
        case "TS-147" => Issue.Types.ABSENCE
        case "TS-345" => Issue.Types.PARENTALLEAVE
        case _ => Issue.Types.OTHER
      }
    }
  }

  object Types extends Enumeration {
    val BILLABLE = Value("Billable")
    val ADMIN = Value("Admin Time")
    val VACATION = Value("Vacation")
    val PRESALES = Value("Pre-Sales")
    val RECRUITING = Value("Recruiting")
    val ILLNESS = Value("Illness")
    val TRAVELTIME = Value("Travel Time")
    val TWENTYPERCENT = Value("20% Time")
    val ABSENCE = Value("Other absence")
    val PARENTALLEAVE = Value("Parental leave")
    val OTHER = Value("Other")
  }

}