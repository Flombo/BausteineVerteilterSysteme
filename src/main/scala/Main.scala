import caseClasses.CSVFileMessage
import akka.actor.Props

object Main extends App {
    val measurementAverageActor = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props[MeasurementAverageActor], name="measurementaverageactor")

    val csvTextActor = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props(new CSVTextActor(measurementAverageActor)), name="csvtextactor")

    val csvFileActor = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props(new CSVFileActor(csvTextActor)), name = "csvfileactor")

    csvFileActor ! CSVFileMessage(".\\src\\main\\resources\\jenaAusschnitt.csv")
}
