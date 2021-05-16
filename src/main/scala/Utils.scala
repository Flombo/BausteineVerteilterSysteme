import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import java.io.File
import java.sql.Timestamp
import java.text.{ParseException, SimpleDateFormat}
import java.util.Date

object Utils {

  private val simpleDateFormat = new SimpleDateFormat("dd.mm.yyyy hh:mm:ss")

  /**
   * parses given string to timestamp over SimpleDateFormat.
   *
   * @param text : String
   * @return
   */
  @throws(classOf[ParseException])
  def parseStringToTimestamp(text: String): Timestamp = {
      val date: Date = simpleDateFormat.parse(text)
      val timestamp = new Timestamp(date.getTime)
      timestamp
  }

  def createSystem(fileName: String, systemName: String): ActorSystem = {
    val resource = getClass.getResource(fileName)
    val configFile=resource.getFile
    val config = ConfigFactory.parseFile(new File(configFile)).resolve()
    val result = ActorSystem(systemName, config)
    result
  }
}
