import akka.actor.{ActorRef, ActorSystem, Props}
import scala.language.postfixOps

object DBHandlerCluster extends App {

  val system : ActorSystem = Utils.createSystem("dbWriter.conf", "bausteineverteiltersysteme")
  val dbHandlerActor : ActorRef = system.actorOf(Props[DBHandlerActor], name="dbhandler")

}

