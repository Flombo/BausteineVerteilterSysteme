import caseClasses.{AverageMeasurementValueMessage, CancelMessage}
import akka.actor.Actor

import java.sql.{Connection, DriverManager, Timestamp}

class DBWriterActor() extends Actor {

  val driver : String = "org.h2.Driver"
  val url : String = "jdbc:h2:tcp://localhost/~/test"
  val username : String = "sa"
  val password : String = ""
  var connection : Connection = null

  /**
   * preStart handler will be called before the actor will be started.
   * A db-connection will be opened before the actor will be started.
   */
  override def preStart(): Unit = {
    super.preStart()
    connectToH2()
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
   * this method inserts a new entry into jena db-table
   * @param date : Timestamp
   * @param degCAVG : Float
   */
  def writeIntoDB(date : Timestamp, degCAVG : Float)  {
    try {
      val preparedStatement = connection.prepareStatement("INSERT INTO JENA(messagetimestamp , degcavg) VALUES(?, ?)")
      preparedStatement.setTimestamp(1, date)
      preparedStatement.setFloat(2, degCAVG)
      preparedStatement.execute()
      preparedStatement.close()
    } catch {
      case e => e.printStackTrace()
    }
  }

  /**
   * receive handler will be called when message was received.
   * If the message is part of the CaseClasses.AverageMeasurementValueMessage case-class, a new entry in jena db-table will be inserted.
   * If the message is part of the CaseClasses.CancelMessage case-class, the open connection will be closed and this actor will also be closed.
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
