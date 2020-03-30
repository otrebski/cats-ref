package server

import java.net.URL

import cats.data.Kleisli
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, ExitCode, Fiber, IO, IOApp, Timer}
import scala.concurrent.duration._
import scala.io.{BufferedSource, Source}
import scala.language.postfixOps
import scala.util.matching.Regex

import cats.implicits._
import server.GovKoronavirusStats.Stats

object ServerApp extends IOApp {

  def loop(update: IO[Option[Stats]], cache: Ref[IO, Option[Stats]]): IO[Unit] = for {
    newContent <- update
    _ <- cache.getAndUpdate(old => if (newContent.isDefined) newContent else old)
//    _ <- cache.set(newContent)
    _ <- Timer[IO].sleep(120 seconds)
    _ <- loop(update, cache)
  } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val port = 8090
    val program = for {
      cache <- Ref.of[IO, Option[Stats]](none[Stats])
      _ <- HttpServer.start(cache, port)
      _ <- IO(println(s"Stared on http://localhost:$port/"))
      _ <- loop(GovKoronavirusStats.read(), cache)
    } yield ()
    program.map(_ => ExitCode.Success)
  }
}

object GovKoronavirusStats {
  case class Stats(date: String, confirmed: Int, deaths: Int)
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

object HttpServer {

  import org.http4s._
  import org.http4s.dsl.io._
  import scala.concurrent.ExecutionContext.Implicits.global
  import org.http4s.server.blaze._
  import org.http4s.implicits._
  import org.http4s.server.Router

  implicit val cs: ContextShift[IO] = IO.contextShift(global)
  implicit val timer: Timer[IO] = IO.timer(global)

  def start(cache: Ref[IO, Option[Stats]], port:Int): IO[Fiber[IO, Nothing]] = {
    val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root =>
        val current: IO[Response[IO]] = for {
          result <- cache.get
          response <- result match {
            case Some(stats) => Ok(s"Update:${stats.date}\nConfirmed: ${stats.confirmed}\nDeaths: ${stats.deaths}")
            case None => Ok("No data")
          }

        } yield response
        current
    }

    val httpApp: Kleisli[IO, Request[IO], Response[IO]] = Router("/" -> service).orNotFound
    val serverBuilder: BlazeServerBuilder[IO] = BlazeServerBuilder[IO]
      .bindHttp(port, "0.0.0.0")
      .withHttpApp(httpApp)

    serverBuilder.resource.use(_ => IO.never).start
  }
}
