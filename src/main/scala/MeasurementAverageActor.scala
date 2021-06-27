import caseClasses.{AverageMeasurementValueMessage, MeasurementValueMessage, StartMovingAverageCalculation}
import akka.actor.{Actor, ActorLogging, ActorSelection, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import scala.math.BigDecimal.double2bigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util

class MeasurementAverageActor extends Actor with ActorLogging {

  val cluster : Cluster = Cluster(context.system)
  var receivedValuesPuffer : util.LinkedList[(Timestamp, Float)] = new util.LinkedList[(Timestamp, Float)]()
  var receivedValues : util.LinkedList[(String, Timestamp, Float)] = new util.LinkedList[(String, Timestamp, Float)]()
  var dbHandlerActor: Option[ActorSelection] = None

  /**
   * preStart function will be executed before the Actor will be started.
   */
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberUp])
  }

  def register(member : Member): Unit = {
    if(member.hasRole("dbhandler")) {
      dbHandlerActor = Some(context.actorSelection(RootActorPath(member.address) / "user" / "dbhandler"))

      receivedValuesPuffer.forEach(entry => {
        dbHandlerActor.get ! AverageMeasurementValueMessage(entry._1, entry._2)
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
      register(member)

    case state : CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up).foreach(register)

    case message : MeasurementValueMessage =>
      receivedValues.addLast((message.filename, message.timestamp, message.degC))

    case startMovingAverageCalculation: StartMovingAverageCalculation =>
      val sentFilename : String = startMovingAverageCalculation.filename

      calcMovingAverage(sentFilename)

      dbHandlerActor match {
        case Some(dbHandlerActor) =>
          receivedValuesPuffer.forEach(entry => {
            dbHandlerActor ! AverageMeasurementValueMessage(entry._1, entry._2)
          })
        case None =>
          println("No DBHandlerActor registered")
      }

  }

  def calcMovingAverage(sentFilename : String): Unit = {
    receivedValues.forEach(entry => {
      val filename : String = entry._1
      val timestamp : Timestamp = entry._2

      if(filename.equals(sentFilename)) {
        val movingAverage : Float = buildMovingAverage(timestamp, filename)
        println(movingAverage + " avg / timestamp " + timestamp)
        receivedValuesPuffer.addLast((timestamp, movingAverage))
      }
    })
  }

  def buildMovingAverage(timestamp: Timestamp, filename : String) : Float = {
    var sumOfRecievedValues : Float = 0

    val now : LocalDateTime = timestamp.toLocalDateTime
    val yesterday : Timestamp = Timestamp.valueOf(now.minusHours(24).minusSeconds(1))

    var measurementAmount = 0

    receivedValues.forEach(entry => {

      if(entry._1.equals(filename)) {
        if (entry._2.compareTo(yesterday) >= 0 && entry._2.compareTo(timestamp) <= 0) {
          sumOfRecievedValues = (sumOfRecievedValues + entry._3).setScale(2, BigDecimal.RoundingMode.HALF_UP).toFloat
          measurementAmount = measurementAmount + 1
        }
      }
    })

    (sumOfRecievedValues / measurementAmount).setScale(2, BigDecimal.RoundingMode.HALF_UP).toFloat
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
