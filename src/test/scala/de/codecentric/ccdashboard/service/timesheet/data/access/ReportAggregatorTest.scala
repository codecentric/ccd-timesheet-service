package de.codecentric.ccdashboard.service.timesheet.data.access

import java.text.SimpleDateFormat

import de.codecentric.ccdashboard.service.timesheet.data.model.UserSchedule
import de.codecentric.ccdashboard.service.timesheet.messages.ReportEntry
import org.scalatest.FunSuite

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class ReportAggregatorTest extends FunSuite {
  val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

  val reports = List(
    ("2015-07-01", ReportEntry(billableHours = Some(8.0))),
    ("2015-07-02", ReportEntry(billableHours = Some(4), adminHours = Some(4))),
    ("2016-08-01", ReportEntry(billableHours = Some(4), adminHours = Some(4))),
    ("2016-08-03", ReportEntry(adminHours = Some(4))),
    ("2016-09-01", ReportEntry(billableHours = Some(8)))
  )

  val workSchedules = List(
    ("abc", "2015-07-01", 8.0),
    ("abc", "2015-07-02", 8.0),
    ("abc", "2015-07-03", 8.0),
    ("abc", "2016-08-01", 8.0),
    ("abc", "2016-08-03", 8.0),
    ("abc", "2016-09-01", 8.0)
  )

  val mappedWorkSchedules = workSchedules.map({ case (username, dateString, hours) => UserSchedule(username, dateFormat.parse(dateString), hours) })
  val mappedReports = reports.map(x => dateFormat.parse(x._1) -> x._2)

  val agg = ReportAggregator(mappedReports, mappedWorkSchedules)

  test("testAggregateDaily") {
    val m = agg.aggregateDaily().reports.map(x => x.key -> x).toMap
    assert((m("2015-07-01").utilization - 1.0).abs < 0.01)
    assert((m("2015-07-02").utilization - 0.5).abs < 0.01)
    assert(m("2015-07-03").utilization.abs < 0.01)
    assert((m("2016-08-01").utilization - 0.5).abs < 0.01)
    assert(m("2016-08-03").utilization.abs < 0.01)
    assert((m("2016-09-01").utilization - 1.0).abs < 0.01)
  }

  test("testAggregateMonthly") {
    val m = agg.aggregateMonthly().reports.map(x => x.key -> x).toMap
    assert((m("2015-07").utilization - 0.5).abs < 0.01)
    assert((m("2016-08").utilization - 0.25).abs < 0.01)
    assert((m("2016-09").utilization - 1.0).abs < 0.01)
  }

  test("testAggregateYearly") {
    val m = agg.aggregateYearly().reports.map(x => x.key -> x).toMap
    assert((m("2015").utilization - 0.5).abs < 0.01)
    assert((m("2016").utilization - 0.5).abs < 0.01)
  }
}
