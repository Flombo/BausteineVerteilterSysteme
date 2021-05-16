import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

import java.io.File
import java.text.ParseException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Utils {

  private val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

  /**
   * parses string to LocalDateTime with DateTimeFormatter
   *
   * @param text : String
   * @throws ParseException
   * @return
   */
  @throws(classOf[ParseException])
  def parseDateTime(text: String): LocalDateTime =
    LocalDateTime.parse(text, format)

  /**
   * formats LocalDateTime to DateTime string.
   *
   * @param date : LocalDateTime
   * @throws ParseException
   * @return
   */
  @throws(classOf[ParseException])
  def toDateTime(date: LocalDateTime): String =
    date.format(format)

  def createSystem(fileName: String, systemName: String): ActorSystem = {
    val resource = getClass.getResource(fileName)
    val configFile=resource.getFile
    val config = ConfigFactory.parseFile(new File(configFile)).resolve()
    val result = ActorSystem(systemName, config)
    result
  }
}
