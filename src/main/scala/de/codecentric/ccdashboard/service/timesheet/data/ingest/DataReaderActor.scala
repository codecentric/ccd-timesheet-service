package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.oauth.{OAuthSignatureHelper, OAuthSignatureHelperConfig}
import spray.json._

import scala.concurrent.duration._
import scala.util.{Failure, Success}


/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

trait DataReaderActor extends Actor with ActorLogging

abstract class BaseDataReaderActor(val dataWriter: ActorRef) extends DataReaderActor {
  import context.dispatcher

  val http = Http(context.system)
  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  def getOAuthConfig: Option[Config]

  lazy val oAuthSigHelper = getOAuthConfig.flatMap(conf => {
    val oAuthConfig = OAuthSignatureHelperConfig.fromConfig(conf)
    Some(new OAuthSignatureHelper(oAuthConfig))
  })

  def handleRequest(uri: Uri, signRequest: Boolean = false, entityHandler: HttpEntity => Unit) = {
    log.info(s"Using URI: $uri")
    val httpRequest = HttpRequest(method = HttpMethods.GET, uri = uri)

    val optSignedRequest = (signRequest, oAuthSigHelper) match {
      case (false, _) => httpRequest
      case (true, Some(helper)) => helper.getSignedHttpRequest(httpRequest)
      case (true, None) =>
        val msg = "Cannot sign request with no provided OAuthSignatureHelper"
        log.error(msg)
        throw new IllegalArgumentException(msg)
    }

    val requestFuture = http.singleRequest(optSignedRequest)

    requestFuture onComplete {
      case Success(response) => response match {
        case HttpResponse(StatusCodes.OK, headers, entity, _) =>
          entityHandler(entity)
        case HttpResponse(code, _, _, _) =>
          log.error("Request failed, response code: " + code)
      }
      case Failure(ex) =>
        log.error(s"Request produced exception: ${ex.getStackTrace}")
    }
  }

  def jsonEntityHandler(entity: HttpEntity)(jsonHandlerFunction: JsValue => Unit) = {
    entity.contentType match {
      case ContentTypes.`application/json` =>
        val strictEntity = entity.toStrict(120.seconds)
        strictEntity onComplete {
          case Success(result) =>
            val resultString = result.getData().decodeString("UTF8")
            val resultStringAST = resultString.parseJson
            jsonHandlerFunction(resultStringAST)
          case Failure(ex) =>
            log.error(ex, "Error when reading from Json")
        }
      case contentType => log.error(s"Invalid Content-Type of response: $contentType")
    }
  }
}
