import caseClasses.CSVFileMessage
import akka.actor.Props
import akka.routing.RoundRobinPool

object Main extends App {
    val measurementAverageActor = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props[MeasurementAverageActor], name="measurementaverageactor")

    val csvTextActor = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props(new CSVTextActor(measurementAverageActor)), name="csvtextactor")

    val csvFileActorRouter = Utils
      .createSystem("client.conf", "bausteineverteiltersysteme")
      .actorOf(Props(new CSVFileActorRouter(csvTextActor)), name="csvfileactorroute")

    csvFileActorRouter ! CSVFileMessage(".\\src\\main\\resources\\jena_tail.csv")
    csvFileActorRouter ! CSVFileMessage(".\\src\\main\\resources\\jena_head.csv")

}


import akka.actor.{Actor, ActorRef, Props}
import caseClasses.CSVFileMessage

class CSVFileActorRouter(csvTextActor : ActorRef) extends Actor {

    val router : ActorRef = context.actorOf(RoundRobinPool(2).props(Props(new CSVFileActor(csvTextActor))), name="router")


    def receive: Receive = {
        case csvFileMessage : CSVFileMessage=>
            router ! csvFileMessage
    }

}