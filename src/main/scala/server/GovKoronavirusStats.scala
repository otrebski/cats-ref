package server

import java.net.URL

import cats.effect.IO

import scala.io.{BufferedSource, Source}
import scala.util.matching.Regex
import cats.implicits._

object GovKoronavirusStats {
  def read(): IO[Option[Stats]] = IO {
    val url = new URL("https://www.gov.pl/web/koronawirus/wykaz-zarazen-koronawirusem-sars-cov-2")
    val source: BufferedSource = Source.fromURL(url)
    val lines = source.getLines()
    val dataLine: Option[String] = lines.find(_.contains("aktualne na"))
    val r: Regex = ".*aktualne na : (\\d{1,2}.\\d{2}.\\d{4} \\d{1,2}:\\d{2}).*Cała Polska;(\\d+);(\\d+).*".r
    dataLine.flatMap {
      case r(date, confirmed, deaths) =>
        Stats(date, confirmed.toInt, deaths.toInt).some
      case _ => None
    }
  }
}
