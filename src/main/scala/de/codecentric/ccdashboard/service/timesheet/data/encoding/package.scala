package de.codecentric.ccdashboard.service.timesheet.data

import java.time._
import java.util.Date

import de.codecentric.ccdashboard.service.timesheet.data.model.Worklog
import io.circe.Encoder
import io.circe.generic.semiauto._
import io.circe.java8.time._
import io.getquill.MappedEncoding

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
package object encoding {
  /* Encoders and decoders for Quill */
  implicit val localDateTimeEncoder = MappedEncoding[LocalDateTime, Date](ldt => Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant))
  implicit val localDateTimeDecoder = MappedEncoding[Date, LocalDateTime](date => Instant.ofEpochMilli(date.getTime).atZone(ZoneId.systemDefault()).toLocalDateTime)

  implicit val localDateEncoder = MappedEncoding[LocalDate, Date](ld => Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant))
  implicit val localDateDecoder = MappedEncoding[Date, LocalDate](date => Instant.ofEpochMilli(date.getTime).atZone(ZoneId.systemDefault()).toLocalDate)

  /* Encoders and decoders for Circe */
  implicit val worklogEncoder: Encoder[Worklog] = deriveEncoder
}
