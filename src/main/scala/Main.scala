import caseClasses.{CSVFileMessage, CancelMessage}
import akka.actor.{ActorSystem, Props}

object Main {
  def main(args: Array[String]): Unit = {
    val dbWriterActor = ActorSystem("Bausteineverteiltersystem")
      .actorOf(Props[DBWriterActor], name = "dbWriterActor")

    val measurementAverageActor = ActorSystem("Bausteineverteiltersystem")
      .actorOf(Props(new MeasurementAverageActor(dbWriterActor)), name="measurementAverageActor")

    val csvTextActor = ActorSystem("Bausteineverteiltersystem")
      .actorOf(Props(new CSVTextActor(measurementAverageActor)), name="csvTextActor")

    val CSVFileActor = ActorSystem("Bausteineverteiltersystem")
      .actorOf(Props(new CSVFileActor(csvTextActor)), name="csvFileActor")

    CSVFileActor ! CSVFileMessage(".\\src\\main\\scala\\jenaAusschnitt.csv")
  }
}
