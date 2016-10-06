package de.codecentric.ccdashboard.service.timesheet.data.ingest.jira

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import com.typesafe.config.Config

import scala.concurrent.duration.FiniteDuration

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class JiraConfig(jiraConf: Config, appConf: Config) {
  val jiraUsersServicePath = jiraConf.getString("get-users-service-path")
  val jiraIssueDetailsServicePath = jiraConf.getString("get-issue-details-service-path")
  val jiraTempoTeamServicePath = jiraConf.getString("tempo.team-service-path")
  val jiraTempoTeamMembersServicePath = jiraConf.getString("tempo.team-members-service-path")
  val jiraTempoWorklogsServicePath = jiraConf.getString("tempo.worklog-service-path")
  val jiraTempoUserScheduleServicePath = jiraConf.getString("tempo.user-schedule-service-path")
  val jiraTempoUserAvailabilityServicePath = jiraConf.getString("tempo.user-membership-availablility-service-path")

  val accessToken = jiraConf.getString("access-token")
  val tempoApiToken = jiraConf.getString("tempo.api-token")
  val consumerPrivateKey = jiraConf.getString("consumer-private-key")

  val timesheetProjectKey = appConf.getString("timesheet-service.data-import.project-key")
  val importStartDate = LocalDate.parse(appConf.getString("timesheet-service.data-import.start-date"))
  val importBatchSizeDays = appConf.getDuration("timesheet-service.data-import.batch-size").toDays
  val importSyncRangeDays = appConf.getDuration("timesheet-service.data-import.sync-range").toDays
  val importSyncInterval = FiniteDuration(appConf.getDuration("timesheet-service.data-import.sync-interval").toMinutes, TimeUnit.MINUTES)
  val importWaitBetweenBatches = {
    val v = appConf.getDuration("timesheet-service.data-import.wait-between-batches")
    FiniteDuration(v.toNanos, TimeUnit.NANOSECONDS)
  }
  val importEndDate = importStartDate.plusDays(importBatchSizeDays)
}
