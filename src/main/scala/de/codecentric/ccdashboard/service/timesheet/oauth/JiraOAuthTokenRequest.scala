package de.codecentric.ccdashboard.service.timesheet.oauth

import akka.http.scaladsl.model.Uri
import com.github.scribejava.core.builder.ServiceBuilder
import com.typesafe.config.ConfigFactory

import scala.io.StdIn

/**
  * Performs the request-response cycle needed for getting an access-token
  *
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
object JiraOAuthTokenRequest extends App {
  val conf = ConfigFactory.load("jiraclient.conf")

  // Read configuration
  val jiraBaseUri = Uri.from(scheme = conf.getString("jira.scheme"), host = conf.getString("jira.host")).toString
  val requestTokenUri = jiraBaseUri + "/plugins/servlet/oauth/request-token"
  val authorizationUri = jiraBaseUri + "/plugins/servlet/oauth/authorize?oauth_token=%s"
  val accessTokenUri = jiraBaseUri + "/plugins/servlet/oauth/access-token"
  val jiraConsumerPrivateKey = conf.getString("jira.consumer-private-key")
  val jiraConsumerKey = conf.getString("jira.consumer-key")

  // Instantiate jiraApi and OAuth service
  val jiraApi = new JiraApi(jiraConsumerPrivateKey, authorizationUri, requestTokenUri, accessTokenUri)
  val service = new ServiceBuilder().apiKey(jiraConsumerKey).build(jiraApi)

  // Request request-token, authorize it, then get get access-token
  val requestToken = service.getRequestToken
  println(s"Received request-token $requestToken")

  val authorizationUrl = service.getAuthorizationUrl(requestToken)
  println(s"Authorization URL: $authorizationUrl")

  val verificationCode = StdIn.readLine("Please open the URL in a browser and enter the shown verification code here: ")
  val accessToken = service.getAccessToken(requestToken, verificationCode)
  println(s"*** Your access-token secret is: ${accessToken.getTokenSecret} ***")
}
