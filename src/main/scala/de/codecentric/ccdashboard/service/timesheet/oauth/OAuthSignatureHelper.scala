package de.codecentric.ccdashboard.service.timesheet.oauth

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.model.{OAuth1AccessToken, OAuthRequest, Verb}
import com.typesafe.config.Config

import scala.collection.JavaConverters._

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

class OAuthSignatureHelper(conf: OAuthSignatureHelperConfig) {
  val jiraApi = new JiraApi(conf.jiraConsumerPrivateKey, conf.authorizationUri, conf.requestTokenUri, conf.accessTokenUri)
  val service = new ServiceBuilder().apiSecret(conf.jiraAccessToken).apiKey(conf.jiraConsumerKey).build(jiraApi)
  val accessToken = new OAuth1AccessToken(conf.jiraAccessToken, conf.jiraConsumerPrivateKey)

  def getSignedHttpRequest(httpRequest: HttpRequest) = {
    val uri = httpRequest.uri.toString
    val oAuthRequest = new OAuthRequest(getHttpVerb(httpRequest.method), uri, service)

    service.signRequest(accessToken, oAuthRequest)

    val signedHeadersMap = oAuthRequest.getHeaders.asScala.toMap
    val signedHttpHeaders = signedHeadersMap.map { case (k, v) => RawHeader(k, v) }.toList

    val allHeaders = List.empty[HttpHeader] ++ httpRequest.headers ++ signedHttpHeaders

    //println(allHeaders)
    HttpRequest(
      method = httpRequest.method,
      uri = httpRequest.uri,
      headers = allHeaders,
      httpRequest.entity,
      httpRequest.protocol)
  }

  /**
    * Mapping between Akka-Http's HttpMethods and ScribeJava's Verbs
    *
    * @param method HttpMethod from HttpRequest
    * @return Verb from ScribeJava
    */
  def getHttpVerb(method: HttpMethod): Verb = {
    method match {
      case HttpMethods.GET => Verb.GET
      case HttpMethods.POST => Verb.POST
      case HttpMethods.DELETE => Verb.DELETE
      case HttpMethods.HEAD => Verb.HEAD
      case HttpMethods.OPTIONS => Verb.OPTIONS
      case HttpMethods.PATCH => Verb.PATCH
      case HttpMethods.PUT => Verb.PUT
      case HttpMethods.TRACE => Verb.TRACE
    }
  }
}

case class OAuthSignatureHelperConfig(jiraBaseUri: String, requestTokenUri: String, authorizationUri: String, accessTokenUri: String, jiraConsumerPrivateKey: String, jiraConsumerKey: String, jiraAccessToken: String)

object OAuthSignatureHelperConfig {
  def fromConfig(conf: Config) = {
    val jiraBaseUri = Uri.from(scheme = conf.getString("scheme"), host = conf.getString("host")).toString
    val requestTokenUri = jiraBaseUri + "/plugins/servlet/oauth/request-token"
    val authorizationUri = jiraBaseUri + "/plugins/servlet/oauth/authorize?oauth_token=%s"
    val accessTokenUri = jiraBaseUri + "/plugins/servlet/oauth/access-token"
    val jiraConsumerPrivateKey = conf.getString("consumer-private-key")
    val jiraConsumerKey = conf.getString("consumer-key")
    val jiraAccessToken = conf.getString("access-token")

    OAuthSignatureHelperConfig(jiraBaseUri, requestTokenUri, authorizationUri, accessTokenUri, jiraConsumerPrivateKey, jiraConsumerKey, jiraAccessToken)
  }
}