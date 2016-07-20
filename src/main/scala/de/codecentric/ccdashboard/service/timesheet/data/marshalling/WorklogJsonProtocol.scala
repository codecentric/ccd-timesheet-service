package de.codecentric.ccdashboard.service.timesheet.data.marshalling

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import de.codecentric.ccdashboard.service.timesheet.data.model._
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat}

/**
  * Created by bjacobs on 19.07.16.
  */
object WorklogJsonProtocol extends DefaultJsonProtocol {

  implicit object LocalDateTimeJsonFormat extends RootJsonFormat[LocalDateTime] {
    private val localDateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    override def write(obj: LocalDateTime) = JsString(localDateTimeFormatter.format(obj))

    override def read(json: JsValue): LocalDateTime = json match {
      case JsString(s) => LocalDateTime.parse(s, localDateTimeFormatter)
      case _ => throw DeserializationException("Error deserializing LocalDateTime")
    }
  }

  implicit object LocalDateJsonFormat extends RootJsonFormat[LocalDate] {
    private val localDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override def write(obj: LocalDate) = JsString(localDateFormatter.format(obj))

    override def read(json: JsValue): LocalDate = json match {
      case JsString(s) => LocalDate.parse(s, localDateFormatter)
      case _ => throw DeserializationException("Error deserializing LocalDate")
    }
  }

  implicit val activityFormat = jsonFormat2(Activity)
  implicit val billingFormat = jsonFormat2(Billing)
  implicit val customFieldsFormat = jsonFormat4(CustomFields)
  implicit val externalFormat = jsonFormat4(External)
  implicit val issueFormat = jsonFormat2(Issue)
  implicit val worklogFormat = jsonFormat15(Worklog)
}