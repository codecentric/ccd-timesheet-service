package de.codecentric.ccdashboard.service.timesheet.rest

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server.Directives
import akka.pattern.ask
import akka.util.Timeout
import de.codecentric.ccdashboard.service.timesheet.messages.{WorklogQuery, WorklogQueryResult}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */

class DataServiceActor(val dataProviderActor: ActorRef) extends Directives {
  /*
    Note: These imports have always to be there since they manage the JSON (un)marshalling.
          Sometimes the IDE tries to optimize them because it doesn't see that they are needed.

    import de.codecentric.ccdashboard.service.timesheet.data.model.MasterJsonProtocol._
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  */

  // TODO: Remove all the ToResponseMarshallable-calls (the T's) when the Scala plugin of IntelliJ was fixed so that it won't show it as an error
  val T = ToResponseMarshallable

  val route =
    path("getWorklogs") {
      get {
        implicit val timeout = Timeout(5.seconds)
        val query = (dataProviderActor ? WorklogQuery(3)).mapTo[WorklogQueryResult]
        onComplete(query) {
          case Success(res) => complete(T(res.w))
          case Failure(ex) => failWith(ex)
        }
      }
    }
}