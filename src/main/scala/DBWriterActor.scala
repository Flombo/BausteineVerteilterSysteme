import caseClasses.{AverageMeasurementResponseMessage, AverageMeasurementValueMessage, CancelMessage, RequestAverageMeasurementMessage}
import akka.actor.{Actor, ActorLogging}

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLDataException, Timestamp}

class DBWriterActor extends Actor with ActorLogging{

  val driver : String = "org.h2.Driver"
  val url : String = "jdbc:h2:tcp://localhost/~/test"
  val username : String = "sa"
  val password : String = ""
  var connection : Connection = null
  var insertPreparedStatement : PreparedStatement = null
  var selectPreparedStatement : PreparedStatement = null

  /**
   * preStart handler will be called before the actor will be started.
   * A db-connection will be opened before the actor will be started.
   */
  override def preStart(): Unit = {
    super.preStart()
    connectToH2()
    createTable()
    insertPreparedStatement = connection.prepareStatement("INSERT INTO JENA(MESSAGETIMESTAMP , DEGCAVG) VALUES(?, ?)")
    selectPreparedStatement = connection.prepareStatement("SELECT DEGCAVG from JENA where MESSAGETIMESTAMP = ?")
  }

  /**
   * creates table jena.
   */
  def createTable() : Unit = {
    val statement = connection.createStatement()
    statement.execute("CREATE TABLE IF NOT EXISTS jena(id bigint not null auto_increment primary key, messagetimestamp timestamp not null, degcavg float not null);")
    statement.close()
  }

  /**
   * opens connection and saves it to connection variable for further usage
   */
  def connectToH2() {
    try {
      Class.forName(driver)
      connection = DriverManager.getConnection(url, username, password)
    } catch {
      case e : Exception => e.printStackTrace()
    }
  }

  /**
   * this method inserts a new entry into jena db-table
   * @param date : Timestamp
   * @param degCAVG : Float
   */
  def writeIntoDB(date : Timestamp, degCAVG : Float)  {
    try {
      insertPreparedStatement.setTimestamp(1, date)
      insertPreparedStatement.setFloat(2, degCAVG)
      insertPreparedStatement.execute()
    } catch {
      case e : Exception => e.printStackTrace()
    }
  }

  /**
   * selects average measurement by given timestamp.
   * if no entry exists for this timestamp, an SQLDataException exception will be thrown.
   *
   * @param timestamp : Timestamp
   * @return
   */
  def selectAverageMeasurementByTimestamp(timestamp: Timestamp): Float = {
    try {
      selectPreparedStatement.setTimestamp(1, timestamp)
      val resultSet : ResultSet = selectPreparedStatement.executeQuery()
      resultSet.next()
      resultSet.getFloat("degcavg")
    } catch {
      case e : SQLDataException => e.printStackTrace()
        throw new Exception
    }
  }

  /**
   * receive handler will be called when message was received.
   * If the message is part of the CaseClasses.AverageMeasurementValueMessage case-class, a new entry in jena db-table will be inserted.
   * If the message is part of the CaseClasses.CancelMessage case-class, the open connection will be closed and this actor will also be closed.
   *If the message is part of the CaseClasses.RequestAverageMeasurementMessage case-class, the moving average will be selected from DB by given timestamp:
   * -if there is an moving average for that timestamp, an AverageMeasurementResponseMessage with the retrieved value will be sent to the sender
   * -else an error is thrown by selectAverageMeasurementByTimestamp,
   *  which will be catched and the sender will retrieve an AverageMeasurementResponseMessage,
   *  where the moving average is null.
   *
   * @return
   */
  def receive(): Receive = {
    case _: CancelMessage =>
      connection.close()
      context.stop(self)
      println("DBWriterActor : ressources closed...")
      println("DBWriterActor : actor closed")

    case message : AverageMeasurementValueMessage =>
      println("DBWriterActor : actor received AverageMeasurementValueMessage: " + message)
      writeIntoDB(message.timestamp, message.averageMeasurement)

    case RequestAverageMeasurementMessage(timestamp) =>
      var averageMeasurement : Option[Float] = null
      try {
        averageMeasurement = Some(selectAverageMeasurementByTimestamp(timestamp))
        sender() ! AverageMeasurementResponseMessage(averageMeasurement)
      } catch {
        case _: Exception => sender() ! AverageMeasurementResponseMessage(averageMeasurement)
      }
  }

  /**
   * if an message is retrieved that isn't a CaseClasses.AverageMeasurementValueMessage or CaseClasses.CancelMessage,
   * this handler will called and print the faulty message
   * @param message : Any
   */
  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    println("Unknown case message : " + message)
  }

}
