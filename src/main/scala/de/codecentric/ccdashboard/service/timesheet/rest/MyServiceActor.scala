package de.codecentric.ccdashboard.service.timesheet.rest

import akka.actor.Actor
import spray.http.MediaTypes._
import spray.routing.HttpService

/**
  * Created by bjacobs on 12.07.16.
  */
class MyServiceActor extends Actor with MyService {
  def receive = runRoute(myRoute)

  def actorRefFactory = context
}

// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

  val myRoute =
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          // XML is marshalled to `text/xml` by default, so we simply override here
          complete {
            <html>
              <body>
                <h1>Say hello to
                  <i>spray-routing</i>
                  on
                  <i>spray-can</i>
                  !</h1>
              </body>
            </html>
          }
        }
      }
    }
}