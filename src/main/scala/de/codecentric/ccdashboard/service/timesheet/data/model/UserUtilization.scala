package de.codecentric.ccdashboard.service.timesheet.data.model

import java.util.Date

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
case class UserUtilization(username: String, day: Date, billableHours: Option[Double], adminHours: Option[Double],
                           vacationHours: Option[Double], preSalesHours: Option[Double], recruitingHours: Option[Double],
                           illnessHours: Option[Double], travelTimeHours: Option[Double], twentyPercentHours: Option[Double],
                           absenceHours: Option[Double], parentalLeaveHours: Option[Double], otherHours: Option[Double]
                          )

object UserUtilization {
  def byDoubleArray(username: String, date: Date, values: List[Double]): UserUtilization =
    new UserUtilization(username, date, Some(values.head), Some(values(1)), Some(values(2)), Some(values(3)), Some(values(4)),
      Some(values(5)), Some(values(6)), Some(values(7)), Some(values(8)), Some(values(9)), Some(values(10)))
}
