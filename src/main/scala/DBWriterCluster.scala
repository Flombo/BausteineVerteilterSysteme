import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{complete, get, pathPrefix}
import akka.http.scaladsl.server.{PathMatchers, Route}
import akka.pattern.ask
import akka.util.Timeout
import caseClasses.{AverageMeasurementResponseMessage, RequestAverageMeasurementMessage}

import java.sql.Timestamp
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.language.postfixOps
import akka.http.scaladsl.server.Directives._

import java.text.ParseException

object DBWriterCluster extends App {

  val system = Utils.createSystem("dbWriter.conf", "bausteineverteiltersysteme")
  val dbwriterActor = system.actorOf(Props[DBWriterActor], name="dbwriteractor")

  /**
   * extracts timestamp from GET Parameter.
   * first the underscore needs to be replaced by whitespaces.
   * then the timestamp will be built in Utils parseStringToTimestamp method and returned.
   *
   * @param timestampString : String
   * @return
   */
  @throws(classOf[ParseException])
  def extractTimestampFromGETParameter(timestampString: String) : Timestamp = {
      val timestampStringWithReplacedUnderScores: String = timestampString.replace('_', ' ')
      Utils.parseStringToTimestamp(timestampStringWithReplacedUnderScores)
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
              val timestampOfParameter: Timestamp = extractTimestampFromGETParameter(timestampString.toString())
              val response: Future[AverageMeasurementResponseMessage] = (dbwriterActor ? RequestAverageMeasurementMessage(timestampOfParameter)).mapTo[AverageMeasurementResponseMessage]
              val averageMeasurementResponseMessage : AverageMeasurementResponseMessage = Await.result(response, timeout.duration)

              if(averageMeasurementResponseMessage.averageMeasurement != null) {
                complete("{ when : " + timestampOfParameter + " what : " + averageMeasurementResponseMessage.averageMeasurement.get + " }")
              } else {
                complete("{ when : " + timestampOfParameter +  " }")
              }

            } catch {
              case exception: Exception =>
                complete("Error occured : " + exception.toString)
            }
        }
      }

    val bindingFuture = Http().newServerAt("localhost", 8080).bind(route)

    println(s"Server online at http://localhost:8080/when\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
