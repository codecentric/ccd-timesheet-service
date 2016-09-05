package de.codecentric.ccdashboard.service.timesheet.data

import java.text.{DateFormat, ParseException}
import java.time._
import java.time.format.{DateTimeFormatter, DateTimeParseException}
import java.util.Date

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.unmarshalling.{Unmarshal, _}
import akka.stream.Materializer
import cats.data.Xor
import de.codecentric.ccdashboard.service.timesheet.data.model.jira.JiraWorklog
import de.codecentric.ccdashboard.service.timesheet.data.model.{Issue, Worklog}
import io.circe.generic.semiauto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, _}
import io.getquill.MappedEncoding

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
package object encoding {
  /* Encoders and decoders for Quill */
  implicit val localDateTimeEncoder = MappedEncoding[LocalDateTime, Date](ldt => Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant))
  implicit val localDateTimeDecoder = MappedEncoding[Date, LocalDateTime](date => Instant.ofEpochMilli(date.getTime).atZone(ZoneId.systemDefault()).toLocalDateTime)

  implicit val localDateEncoder = MappedEncoding[LocalDate, Date](ld => Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant))
  implicit val localDateDecoder = MappedEncoding[Date, LocalDate](date => Instant.ofEpochMilli(date.getTime).atZone(ZoneId.systemDefault()).toLocalDate)

  implicit val stringMapEncoder = MappedEncoding[Map[String, String], String](map => map.asJson.noSpaces)
  implicit val stringMapDecoder = MappedEncoding[String, Map[String, String]](str => {
    decode[Map[String, String]](str) match {
      case Xor.Left(ex) => throw ex
      case Xor.Right(map) => map
    }
  })
  implicit val stringMapMapEncoder = MappedEncoding[Map[String, Map[String, String]], String](map => map.asJson.noSpaces)
  implicit val stringMapMapDecoder = MappedEncoding[String, Map[String, Map[String, String]]](str => {
    decode[Map[String, Map[String, String]]](str) match {
      case Xor.Left(ex) => throw ex
      case Xor.Right(map) => map
    }
  })

  implicit val stringTupleEncoder = MappedEncoding[(String, String), String](t => t.toString)

  /* Encoders and decoders for Circe */
  implicit val encodeDate: Encoder[Date] = Encoder.instance[Date](date =>
    Json.fromString(DateTimeFormatter.ISO_DATE.format(date.toInstant))
  )

  implicit val decodeDate: Decoder[Option[Date]] = Decoder.instance(c =>
    c.as[String].flatMap { s =>
      if ("" == s) Xor.right(None)
      else {
        try Xor.right(Some(localDateEncoder.f(LocalDate.from(DateTimeFormatter.ISO_DATE.parse(s))))) catch {
          case ex: DateTimeParseException => Xor.left(DecodingFailure("Could not parse Date", c.history))
        }
      }
    }
  )

  implicit val worklogEncoder: Encoder[Worklog] = deriveEncoder

  implicit val issueEncoder: Encoder[Issue] = deriveEncoder

  /* XML Marshallers */
  val jiraWorklogUnmarshaller = new FromEntityUnmarshaller[List[JiraWorklog]]() {
    override def apply(value: HttpEntity)(implicit ec: ExecutionContext, materializer: Materializer): Future[List[JiraWorklog]] = {
      Unmarshal(value).to[String].map(s => {
        val xml = XML.loadString(s)
        xml.child.map(JiraWorklog.fromXml)
      }.toList)
    }
  }
}
