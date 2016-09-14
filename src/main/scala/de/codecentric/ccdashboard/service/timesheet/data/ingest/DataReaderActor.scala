package de.codecentric.ccdashboard.service.timesheet.data.ingest

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.config.Config
import de.codecentric.ccdashboard.service.timesheet.oauth.{OAuthSignatureHelper, OAuthSignatureHelperConfig}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

trait DataReaderActor extends Actor with ActorLogging

abstract class BaseDataReaderActor(val dataWriter: ActorRef) extends DataReaderActor {

  protected def scheme: String

  protected def host: String

  protected def authority: Uri.Authority

  protected def getOAuthConfig: Option[Config]

  import context.dispatcher

  lazy val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = Http(context.system).outgoingConnectionHttps(host)

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  lazy val oAuthSigHelper = getOAuthConfig.flatMap(conf => {
    val oAuthConfig = OAuthSignatureHelperConfig.fromConfig(conf)
    Some(new OAuthSignatureHelper(oAuthConfig))
  })

  def handleRequest(uri: Uri, signRequest: Boolean = false, successEntityHandler: HttpEntity => Unit, failureHandler: Throwable => Unit) = Try {
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

    val requestFuture = Source.single(optSignedRequest).via(connectionFlow).runWith(Sink.head)

    requestFuture.onComplete {
      case Success(response) => response match {
        case HttpResponse(StatusCodes.OK, headers, entity, _) =>
          successEntityHandler(entity)
        case HttpResponse(code, _, _, _) =>
          val exception = new RuntimeException(s"Response code was not 404 OK but: $code")
          failureHandler(exception)
      }
      case Failure(ex) =>
        failureHandler(ex)
    }
  }

  def jsonEntityHandler(entity: HttpEntity)(jsonHandlerFunction: String => Unit) = {
    entity.contentType match {
      case ContentTypes.`application/json` =>
        val strictEntity = entity.toStrict(120.seconds)
        strictEntity onComplete {
          case Success(result) =>
            val resultString = result.getData().decodeString("UTF8")
            jsonHandlerFunction(resultString)
          case Failure(ex) =>
            log.error(ex, "Error when reading from Json")
        }
      case contentType => log.error(s"Invalid Content-Type of response: $contentType")
    }
  }
}
