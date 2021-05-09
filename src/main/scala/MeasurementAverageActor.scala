import caseClasses.{AverageMeasurementValueMessage, MeasurementValueMessage}
import akka.actor.{Actor, ActorRef}

import java.sql.{Connection, DriverManager, Timestamp}

class MeasurementAverageActor(dbWriterActor : ActorRef) extends Actor{

  val driver : String = "org.h2.Driver"
  val url : String = "jdbc:h2:tcp://localhost/~/test"
  val username : String = "sa"
  val password : String = ""
  var connection : Connection = null

  override def preStart(): Unit = {
    super.preStart()
    createDBTables()
  }

  /**
   * opens connection and saves it to connection variable for further usage
   */
  def connectToH2() {
    try {
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
    } catch {
      case e => e.printStackTrace()
    }
  }

  /**
   * reads all measurements within the last 24 hrs by given timestamp and returns the average
   *
   * @param timestamp : Timestamp
   * @return
   */
  def readMeasurementsFromDBAndBuildAverage(timestamp: Timestamp) : Float = {
    connectToH2()
    val prepareStatement = connection.prepareStatement("SELECT AVG(DEGC) as DEGCAVG from JENAMEASUREMENTS where MESSAGETIMESTAMP >= ? AND MESSAGETIMESTAMP <= ?;")

    //builds the timestamp for yesterday from given timestamp
    val yesterday = Timestamp.valueOf(timestamp.toLocalDateTime.minusHours(24))

    prepareStatement.setString(1, yesterday.toString)
    prepareStatement.setString(2, timestamp.toString)
    val resultSet = prepareStatement.executeQuery()
    resultSet.next()

    val degcavg: Float = resultSet.getFloat("DEGCAVG")

    resultSet.close()
    prepareStatement.close()
    this.connection.close()

    degcavg
  }

  /**
   *writes timestamps and measurements into jenameasurements table for further processing
   *
   * @param timestamp : Timestamp
   * @param degC : Float
   */
  def writeMeasurementIntoMeasurementTable(timestamp: Timestamp, degC : Float): Unit = {
    connectToH2()
    val prepareStatement = connection.prepareStatement("INSERT INTO JENAMEASUREMENTS(MESSAGETIMESTAMP , DEGC) values (? ,? );")

    prepareStatement.setTimestamp(1, timestamp)
    prepareStatement.setFloat(2, degC)

    prepareStatement.execute()
    prepareStatement.close()
    this.connection.close()
  }

  /**
   * creates tables 'jena' and 'jenameasurments' if they don't already exist.
   */
  def createDBTables(): Unit = {
    connectToH2()

    var prepareStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS jena(id bigint not null auto_increment primary key, messagetimestamp timestamp not null, degcavg float not null);")
    prepareStatement.execute()
    prepareStatement.close()

    prepareStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS jenameasurements(id bigint not null auto_increment primary key, messagetimestamp timestamp not null, degc float not null);")
    prepareStatement.execute()
    prepareStatement.close()

    connection.close()
  }

  /**
   * receive handler will be called when message was received.
   * If the message is part of the CaseClasses.MeasurementValueMessage case-class:
   *  -the writeMeasurementIntoMeasurementTable function will be called with the timestamp and measurement attributes of the message.
   *  -after that, the readMeasurementsFromDBAndBuildAverage function will be called with the timestamp of the message for retrieving the moving average of the last 24hrs.
   *  -in the end a AverageMeasurementValueMessage will be sent to the DBWriterActor actor built by the message timestamp and the created moving average.
   * @return
   */
  def receive(): Receive = {
    case message : MeasurementValueMessage =>
      writeMeasurementIntoMeasurementTable(message.timestamp, message.degC)

      val averageMeasurements = readMeasurementsFromDBAndBuildAverage(message.timestamp)
      println("Average measurements of the last 24 hrs : " + averageMeasurements + " at timestamp : " + message.timestamp)

      dbWriterActor ! AverageMeasurementValueMessage(message.timestamp, averageMeasurements)
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

}
