package de.codecentric.ccdashboard.service.timesheet.data.access

import java.time._
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.{UserSchedule, UserUtilization}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class WorkScheduleServiceTest extends WordSpecLike with Matchers with BeforeAndAfterAll with MockFactory {
  val localDate: LocalDate = LocalDate.parse("2016-01-02")
  val localDateTime: LocalDateTime = localDate.atStartOfDay()
  val clockInstant: Instant = localDateTime.toInstant(ZoneOffset.UTC)
  val clock: Clock = Clock.fixed(clockInstant, ZoneId.systemDefault())

  "WorkScheduleService" should {

    "calculate correct values for provided data" in {
      val userSchedules = List(UserSchedule("john.doe", Date.from(clockInstant.minusSeconds(1)), 8.0))
      val userReports = List(UserUtilization("john.doe", Date.from(clockInstant.minusSeconds(1)), Some(8.0), None, None, None, None, None, None, None, None, None, None))
      val employeeSince = None
      val year = 2016

      val service = new WorkScheduleService(userSchedules, userReports, employeeSince, year, Some(clock))

      assert(service.targetHoursThisYear == 1440)
      assert((service.parentalLeaveDaysThisYear - 0.0).abs < 0.00001)
      assert(service.userStartOfYear.equals(Date.from(LocalDateTime.parse("2016-01-01T00:00:00").atZone(clock.getZone).toInstant)))
      assert(service.vacationDaysThisYear == 30L)
      assert(service.workDaysThisYear == 1)
      assert((service.userWorkDaysThisYear - 1.0).abs < 0.00001)
      assert((service.userWorkDaysAvailabilityRate - 1.0).abs < 0.00001)
    }

    "calculate correct values for provided data with parental leave" in {
      val userSchedules = List(UserSchedule("john.doe", Date.from(clockInstant.minusSeconds(1)), 8.0))
      val userReports = List(UserUtilization("john.doe", Date.from(clockInstant.minusSeconds(1)), Some(4), None, None, None, None, None, None, None, None, Some(4), None))
      val employeeSince = None
      val year = 2016

      val service = new WorkScheduleService(userSchedules, userReports, employeeSince, year, Some(clock))

      assert(service.targetHoursThisYear == (1440 - 4 * 0.8))
      assert((service.parentalLeaveDaysThisYear - 0.5).abs < 0.00001)
      assert(service.vacationDaysThisYear == 30L)
    }
  }
}
