package de.codecentric.ccdashboard.service.timesheet.util

import java.time.{Clock, LocalDate, LocalDateTime}
import java.util.Date

/**
  * Created by tbinias on 24.12.16.
  */
object DateConversions {

  final implicit class ToLocalDateConverter(val date: Date) extends AnyVal {
    def asLocalDate: LocalDate = {
      date.toInstant.atZone(Clock.systemDefaultZone().getZone).toLocalDate
    }
    def asLocalDate(clock: Clock): LocalDate = {
      date.toInstant.atZone(clock.getZone).toLocalDate
    }
  }

  final implicit class LocalDateToUtilDateConverter(val localDate: LocalDate) extends AnyVal {
    def asUtilDate: Date = {
      Date.from(localDate.atStartOfDay().atZone(Clock.systemDefaultZone().getZone).toInstant)
    }
    def asUtilDate(clock: Clock): Date = {
      Date.from(localDate.atStartOfDay().atZone(clock.getZone).toInstant)
    }
  }

  final implicit class LocalDateTimeToUtilDateConverter(val localDateTime: LocalDateTime) extends AnyVal {
    def asUtilDate: Date = {
      Date.from(localDateTime.atZone(Clock.systemDefaultZone().getZone).toInstant)
    }
    def asUtilDate(clock: Clock): Date = {
      Date.from(localDateTime.atZone(clock.getZone).toInstant)
    }
  }
}
