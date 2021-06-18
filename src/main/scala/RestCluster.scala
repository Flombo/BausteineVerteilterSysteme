import akka.actor.{Actor, ActorLogging, ActorRef, ActorSelection, ActorSystem, Props, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
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
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.language.postfixOps

class RestActor extends Actor with ActorLogging{

  val cluster : Cluster = Cluster(context.system)
  var dbHandlerActor: Option[ActorSelection] = None
  implicit val timeout: Timeout = Timeout(6 seconds)

  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }

  def register(member : Member): Unit = {
    if(member.hasRole("dbhandler")) {
      log.info("found dbhandler : " + member)
      dbHandlerActor = Some(context.actorSelection(RootActorPath(member.address) / "user" / "dbhandler"))
    }
  }

  def receive(): Receive = {
    case MemberUp(member) =>
      log.info("recieved : " + member)
      register(member)

    case state : CurrentClusterState =>
      log.info("recieved currentClusterState : " + state)
      state.members.filter(_.status == MemberStatus.Up).foreach(register)

    case requestAverageMeasurementMessage : RequestAverageMeasurementMessage =>

      dbHandlerActor match {
        case Some(dbHandlerActor) =>

          val future : Future[AverageMeasurementResponseMessage] = (dbHandlerActor ? requestAverageMeasurementMessage).mapTo[AverageMeasurementResponseMessage]
          val response = Await.result(future, timeout.duration)
          sender ! response

        case None =>
          log.info("No DBHandlerActor active...")
      }
  }
}

object RestCluster extends App{

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

    implicit val system: ActorSystem = Utils.createSystem("restInterface.conf", "bausteineverteiltersysteme")
    val restActor : ActorRef = system.actorOf(Props[RestActor], name="restActor")
    implicit val executionContext : ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(6 seconds)

    val route: Route =
      get {
        pathPrefix("when" / PathMatchers.RemainingPath) {
          timestampString =>
            try {
              val localDateTimeOfParameter : LocalDateTime = extractLocalDateTimeFromGETParameter(timestampString.toString())

              val response: Future[AverageMeasurementResponseMessage] = (
                restActor ? RequestAverageMeasurementMessage(
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
