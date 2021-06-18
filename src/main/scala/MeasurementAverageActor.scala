import caseClasses.{AverageMeasurementValueMessage, MeasurementValueMessage}
import akka.actor.{Actor, ActorLogging, ActorSelection, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import org.h2.value.ValueDecimal.setScale

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util
import scala.math.BigDecimal.double2bigDecimal

class MeasurementAverageActor extends Actor with ActorLogging {

  val cluster : Cluster = Cluster(context.system)
  var receivedValuesPuffer : util.HashMap[Timestamp, Float] = new util.HashMap[Timestamp, Float]()
  var receivedValues : util.HashMap[Timestamp, Float] = new util.HashMap[Timestamp, Float]()
  var dbHandlerActor: Option[ActorSelection] = None

  /**
   * preStart function will be executed before the Actor will be started.
   */
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }

  def register(member : Member): Unit = {
    if(member.hasRole("dbhandler")) {
      log.info("found dbhandler : " + member)
      dbHandlerActor = Some(context.actorSelection(RootActorPath(member.address) / "user" / "dbhandler"))

      receivedValuesPuffer.forEach((timestamp, movingAverage) => {
        dbHandlerActor.get ! AverageMeasurementValueMessage(timestamp = timestamp, averageMeasurement = movingAverage)
      })
    }
  }

  /**
   * receive handler will be called when message was received.
   * If the message is part of the CaseClasses.MeasurementValueMessage case-class:
   *  -the writeMeasurementIntoMeasurementTable function will be called with the timestamp and measurement attributes of the message.
   *  -in the end the recieved needs to be persisted, for later usage, when the dbHandlerActor is up.
   *
   * @return
   */
  def receive(): Receive = {
    case MemberUp(member) =>
      log.info("recieved : " + member)
      register(member)

    case state : CurrentClusterState =>
      log.info("recieved currentClusterState : " + state)
      state.members.filter(_.status == MemberStatus.Up).foreach(register)

    case message : MeasurementValueMessage =>
      receivedValues.put(message.timestamp, message.degC)
      val movingAverage : Float = buildMovingAverage(message.timestamp)

      log.info("movingAverage : " + movingAverage + " timestamp : " + message.timestamp)

      dbHandlerActor match {
        case Some(dbHandlerActor) =>
          dbHandlerActor ! AverageMeasurementValueMessage(message.timestamp, movingAverage)
        case None =>
          receivedValuesPuffer.put(message.timestamp, movingAverage)
      }
  }

  def buildMovingAverage(timestamp: Timestamp) : Float = {
    var sumOfRecievedValues : Float = 0
    var removableTimestamp: Timestamp = null

    val now : LocalDateTime = timestamp.toLocalDateTime
    val yesterday : Timestamp = Timestamp.valueOf(now.minusHours(24))

    receivedValues.keySet.forEach(timestamp => {
      if(yesterday.compareTo(timestamp) > 0) {
        removableTimestamp = timestamp
      }
    })

    receivedValues.remove(removableTimestamp)

    receivedValues.values.forEach(degC => {
      sumOfRecievedValues = sumOfRecievedValues + degC
    })

    val sizeOfRecievedValues = receivedValues.size

    sumOfRecievedValues / sizeOfRecievedValues
  }

  /**
   * if an message is retrieved that isn't a CaseClasses.MeasurementValueMessage,
   * this handler will called and print the faulty message
   * @param message : Any
   */
  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    println("MeasurementAverageActor : faulty message type received: " + message)
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

}
