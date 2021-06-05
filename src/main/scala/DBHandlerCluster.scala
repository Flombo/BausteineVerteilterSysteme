import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, pathPrefix, _}
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.pattern.ask
import akka.util.Timeout
import caseClasses.{AverageMeasurementResponseMessage, RequestAverageMeasurementMessage}
import java.sql.Timestamp
import java.text.ParseException
import java.time.LocalDateTime
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

object DBHandlerCluster extends App {

  val system : ActorSystem = Utils.createSystem("dbWriter.conf", "bausteineverteiltersysteme")
  val dbHandlerActor : ActorRef = system.actorOf(Props[DBHandlerActor], name="dbhandler")

  /**
   * extracts timestamp from GET Parameter.
   * first the underscore needs to be replaced by whitespaces.
   * then the timestamp will be built in Utils parseStringToTimestamp method and returned.
   *
   * @param timestampString : String
   * @return
   */
  @throws(classOf[ParseException])
  def extractLocalDateTimeFromGETParameter(timestampString: String) : LocalDateTime = {
    val timestampStringWithReplacedUnderScores: String = timestampString.replace('_', ' ')
    Utils.parseDateTime(timestampStringWithReplacedUnderScores)
  }

  startHTTPServer()

  /**
   * starts the http server.
   * if the "when" route will be called, the remaining path will be extracted, as timestampString.
   * after that, the timestamp will be extracted and sent over the ask pattern to the DBWriterActor.
   * the result is a future of AverageMeasurementResponseMessage, which could contain the wanted moving average as float or null.
   * if the retrieved value isn't null a response with the given timestamp and the value will be sent as json-object.
   * else only, the timestamp will be returned.
   * if an exception occur (in example: the response of the DBWriterActor isn't retrieved in given timeout), a third response will be sent, with an exception message.
   *
   *
   */
  def startHTTPServer(): Unit = {

    implicit val system: ActorSystem = ActorSystem()
    implicit val executionContext : ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(10 seconds)

    val route: Route =
      get {
        pathPrefix("when" / PathMatchers.RemainingPath) {
          timestampString =>
            try {
              val localDateTimeOfParameter : LocalDateTime = extractLocalDateTimeFromGETParameter(timestampString.toString())

              val response: Future[AverageMeasurementResponseMessage] = (
                dbHandlerActor ? RequestAverageMeasurementMessage(
                  Timestamp.valueOf(localDateTimeOfParameter)
                )
                ).mapTo[AverageMeasurementResponseMessage]

              onComplete(response) {
                averageMeasurementResponseMessage =>

                  if (averageMeasurementResponseMessage.isSuccess) {

                    if (averageMeasurementResponseMessage.get.averageMeasurement.isDefined) {
                      complete("{ when : " + Utils.toDateTime(localDateTimeOfParameter) + ", what : " + averageMeasurementResponseMessage.get.averageMeasurement.get + " }")
                    } else {
                      complete("{ when : " + Utils.toDateTime(localDateTimeOfParameter) + " }")
                    }

                  } else {
                    complete("{ when : " + Utils.toDateTime(localDateTimeOfParameter) + " }")
                  }

              }

            } catch {
              case exception: Exception =>
                complete("Error occured : " + exception.toString)
            }
        }
      }
    Http().newServerAt("localhost", 8080).bind(route)
  }
}

