package de.codecentric.ccdashboard.service.timesheet.data.ingest.jira

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class JiraConfig(conf: Config) {
  val jiraUsersServicePath = conf.getString("get-users-service-path")
  val jiraIssueDetailsServicePath = conf.getString("get-issue-details-service-path")
  val jiraTempoTeamServicePath = conf.getString("tempo.team-service-path")
  val jiraTempoTeamMembersServicePath = conf.getString("tempo.team-members-service-path")
  val jiraTempoWorklogsServicePath = conf.getString("tempo.worklog-service-path")
  val jiraTempoUserScheduleServicePath = conf.getString("tempo.user-schedule-service-path")
  val jiraTempoUserAvailabilityServicePath = conf.getString("tempo.user-membership-availablility-service-path")

  val accessToken = conf.getString("access-token")
  val tempoApiToken = conf.getString("tempo.api-token")
  val consumerPrivateKey = conf.getString("consumer-private-key")

  val timesheetProjectKey = conf.getString("timesheet-service.data-import.project-key")
  val importStartDate = LocalDate.parse(conf.getString("timesheet-service.data-import.start-date"))
  val importBatchSizeDays = conf.getDuration("timesheet-service.data-import.batch-size").toDays
  val importSyncRangeDays = conf.getDuration("timesheet-service.data-import.sync-range").toDays
  val importSyncInterval = FiniteDuration(conf.getDuration("timesheet-service.data-import.sync-interval").toMinutes, TimeUnit.MINUTES)
  val importWaitBetweenBatches = {
    val v = conf.getDuration("timesheet-service.data-import.wait-between-batches")
    FiniteDuration(v.toNanos, TimeUnit.NANOSECONDS)
  }
  val importEndDate = importStartDate.plusDays(importBatchSizeDays)
}
