package de.codecentric.ccdashboard.service.timesheet.util

import java.time.{LocalDate, LocalDateTime, ZoneId}
import java.util.Date

/**
  * Created by tbinias on 24.12.16.
  */
object DateConversions {

  final class ToLocalDateConverter(val date: Date) {
    def asLocalDate: LocalDate = {
      date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }
  }

  final class LocalDateToUtilDateConverter(val localDate: LocalDate) {
    def asUtilDate: Date = {
      Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())
    }
  }

  final class LocalDateTimeToUtilDateConverter(val localDateTime: LocalDateTime) {
    def asUtilDate: Date = {
      Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())
    }
  }

  implicit def forUtilDate(date: Date): ToLocalDateConverter =
    new ToLocalDateConverter(date)

  implicit def forLocalDate(localDate: LocalDate): LocalDateToUtilDateConverter =
    new LocalDateToUtilDateConverter(localDate)

  implicit def forLocalDateTime(localDateTime: LocalDateTime): LocalDateTimeToUtilDateConverter =
    new LocalDateTimeToUtilDateConverter(localDateTime)
}
