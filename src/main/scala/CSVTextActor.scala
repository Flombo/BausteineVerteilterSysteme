import akka.actor.{Actor, ActorRef}
import caseClasses.{CSVTextMessage, MeasurementValueMessage}

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

class CSVTextActor(measurementAverageActor : ActorRef) extends Actor{

  /**
   * receive handler will be called when message was received.
   * if the message is part of the CaseClasses.CSVTextMessage case-class,
   * the given text property will be processed in the extractTimestampAndMeasurementValueFromText-function.
   *
   * @return
   */
  def receive(): Receive = {
    case message : CSVTextMessage =>
      measurementAverageActor ! extractTimestampAndMeasurementValueFromText(message.text)
  }

  /**
   * this function extracts the timestamp and the measurement form the given string.
   * after that a MeasurementValueMessage case-class will be built from these extracted properties
   *
   * @param text : String
   * @return
   */
  def extractTimestampAndMeasurementValueFromText(text : String): MeasurementValueMessage = {
      val allValuesSplitByComma : Array[String] = text.split(',')
      val timestamp : Timestamp = extractTimestampFromText(allValuesSplitByComma)
      val measurement : Float = allValuesSplitByComma(2).toFloat
      MeasurementValueMessage(timestamp, measurement)
  }

  /**
   * function extracts timestamp value from given string-array.
   * first 0th index will be cast to string,
   * because the wanted timestamp stands at the first position in the csv.
   * after that a SimpleDateFormat will be created for formatting.
   * next a Date property will be created by parsing the dateString with the created SimpleDateFormat.
   * finally a new instance of Timestamp class will be returned.
   * the Timestamp needs a time parameter retrieved by the getTime function of Date property.
   *
   * @param allValuesSplitByComma : Array[String]
   * @return
   */
  def extractTimestampFromText(allValuesSplitByComma : Array[String]) : Timestamp = {
    val dateTxt : String = allValuesSplitByComma(0)
    val simpleDateFormat : SimpleDateFormat = new SimpleDateFormat("dd.mm.yyyy hh:mm:ss");
    val date : Date = simpleDateFormat.parse(dateTxt)
    new Timestamp(date.getTime)
  }

  /**
   * if an message is retrieved that isn't a CaseClasses.CSVTextMessage,
   * this handler will called and print the faulty message
   *
   * @param message : Any
   */
  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    println("CSVTextActor: faulty message retrieved: " + message)
  }
}
