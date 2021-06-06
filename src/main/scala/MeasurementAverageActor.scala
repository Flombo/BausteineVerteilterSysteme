import caseClasses.{AverageMeasurementValueMessage, MeasurementValueMessage}
import akka.actor.{Actor, ActorLogging, ActorSelection, RootActorPath}
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import java.sql.{Connection, DriverManager, Timestamp}
import java.util

class MeasurementAverageActor extends Actor with ActorLogging {

  val driver : String = "org.h2.Driver"
  val url : String = "jdbc:h2:tcp://localhost/~/test"
  val username : String = "sa"
  val password : String = ""
  val cluster : Cluster = Cluster(context.system)
  var receivedTimestamps : util.LinkedList[Timestamp] = new util.LinkedList[Timestamp]()

  /**
   * preStart function will be executed before the Actor will be started.
   * builds db tables jena and jenameasurements if the don't already exist.
   */
  override def preStart(): Unit = {
    createDBTables()
    cluster.subscribe(self, classOf[MemberUp])
  }

  /**
   * opens connection and returns it for further usage
   */
  def connectToH2(): Option[Connection] = {
    var connection: Option[Connection] = None
    try {
      Class.forName(driver)
      connection = Some(DriverManager.getConnection(url, username, password))
    } catch {
      case e : Exception => e.printStackTrace()
    }
    connection
  }

  /**
   * reads all measurements within the last 24 hrs by given timestamp and returns the average
   *
   * @param timestamp : Timestamp
   * @return
   */
  def readMeasurementsFromDBAndBuildAverage(timestamp: Timestamp) : Float = {
    val connection : Option[Connection] = connectToH2()
    var degcavg: Float = 0

    if(connection.isDefined) {
      if (connection != null) {
        val prepareStatement = connection.get.prepareStatement("SELECT AVG(DEGC) as DEGCAVG from JENAMEASUREMENTS where MESSAGETIMESTAMP >= ? AND MESSAGETIMESTAMP <= ?;")

        //builds the timestamp for yesterday from given timestamp
        val yesterday = Timestamp.valueOf(timestamp.toLocalDateTime.minusHours(24))

        prepareStatement.setString(1, yesterday.toString)
        prepareStatement.setString(2, timestamp.toString)
        val resultSet = prepareStatement.executeQuery()
        resultSet.next()

        degcavg = resultSet.getFloat("DEGCAVG")

        resultSet.close()
        prepareStatement.close()
        connection.get.close()
      }
    }
    degcavg
  }

  /**
   *writes timestamps and measurements into JENAMEASUREMENTS table for further processing
   *
   * @param timestamp : Timestamp
   * @param degC : Float
   */
  def writeMeasurementIntoMeasurementTable(timestamp: Timestamp, degC : Float): Unit = {
    val connection : Option[Connection] = connectToH2()

    if(connection.isDefined) {
      val prepareStatement = connection.get.prepareStatement("INSERT INTO JENAMEASUREMENTS(MESSAGETIMESTAMP , DEGC) values (? ,? );")

      prepareStatement.setTimestamp(1, timestamp)
      prepareStatement.setFloat(2, degC)

      prepareStatement.execute()
      prepareStatement.close()
      connection.get.close()
    }
  }

  /**
   * creates tables 'jena' and 'jenameasurments' if they don't already exist.
   */
  def createDBTables(): Unit = {
    val connection : Option[Connection] = connectToH2()

    if(connection.isDefined) {
      var prepareStatement = connection.get.prepareStatement("CREATE TABLE IF NOT EXISTS jena(id bigint not null auto_increment primary key, messagetimestamp timestamp not null, degcavg float not null);")
      prepareStatement.execute()
      prepareStatement.close()

      prepareStatement = connection.get.prepareStatement("CREATE TABLE IF NOT EXISTS jenameasurements(id bigint not null auto_increment primary key, messagetimestamp timestamp not null, degc float not null);")
      prepareStatement.execute()
      prepareStatement.close()

      connection.get.close()
    }
  }

  def register(member : Member): Unit = {
    if(member.hasRole("dbhandler")) {
      log.info("found dbhandler : " + member)
      sendAverageMeasurementValueMessageToDBHandlerActor(context.actorSelection(RootActorPath(member.address) / "user" / "dbhandler"))
    }
  }

  def sendAverageMeasurementValueMessageToDBHandlerActor(dbHandlerActor : ActorSelection): Unit = {
      receivedTimestamps.forEach(receivedTimestamp => {
        val averageMeasurement = readMeasurementsFromDBAndBuildAverage(receivedTimestamp)
        println("Average measurements of the last 24 hrs : " + averageMeasurement + " at timestamp : " + receivedTimestamp)
        dbHandlerActor ! AverageMeasurementValueMessage(receivedTimestamp, averageMeasurement)
      })

      receivedTimestamps.clear()
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
      log.info("recieved currentCluserState : " + state)
      state.members.filter(_.status == MemberStatus.Up).foreach(register)

    case message : MeasurementValueMessage =>
      writeMeasurementIntoMeasurementTable(message.timestamp, message.degC)
      receivedTimestamps.add(message.timestamp)
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
