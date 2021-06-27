import akka.actor.{Actor, ActorRef}
import caseClasses.{CSVFileActorFinishedMessage, CSVFileMessage, CSVTextMessage}

import scala.io.{BufferedSource, Source}

class CSVFileActor (csvTextActor:  ActorRef) extends Actor{

  /**
   * receive handler will be called when message was received.
   * If the message is part of the CaseClasses.CSVFileMessage case-class,
   * the given filename property will be processed in the extractRowsFromFileAndSendRowToCSVTextActor-function.
   * @return
   */
  def receive(): Receive = {
    case message : CSVFileMessage =>
      extractRowsFromFileAndSendRowToCSVTextActor(message.filename)
  }

  /**
   * extracts rows from file by given filename.
   * first a BufferedSource will be built fromFile by given filename
   * Then foreach row/line:
   *  -first it  will be checked if the first character isn't a number:
   *    -if that's the case we are in line 1, where the comment is situated.
   *    -if this isn't the case, a CSVTextMessage with the extracted row will be sent to the CSVTextActor.
   * Finally the BufferedSource will be closed.
   *
   * @param filename : String
   */
  def extractRowsFromFileAndSendRowToCSVTextActor(filename : String): Unit = {
    val bufferedSource : BufferedSource = Source.fromFile(filename)

    bufferedSource.getLines().foreach(row => {
      if(row.charAt(0) != '"')
        csvTextActor ! CSVTextMessage(row, filename)
    })

    bufferedSource.close()

    csvTextActor ! CSVFileActorFinishedMessage(filename)
  }

  /**
   * if an message is retrieved that isn't a CaseClasses.CSVFileMessage,
   * this handler will called and print the faulty message
   *
   * @param message : Any
   */
  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    println("CSVFileActor : faulty message type received: " + message)
  }

}
