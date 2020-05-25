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
  val port = 8090

  override def run(args: List[String]): IO[ExitCode] = {
    val program = for {
      cache <- Ref.of[IO, Option[Stats]](none[Stats])        //1. Create cache F[Ref[F[_], A]]
      //of[F[_], A](a: A)(implicit F: Sync[F]): F[Ref[F, A]]
      _ <- HttpServer.start(cache, port)                     //2. Pass cache to other thread
      _ <- IO(println(s"Stared on http://localhost:$port/"))
      _ <- loop(GovKoronavirusStats.read(), cache)           //3. Start program using cache
    } yield ()
    program.map(_ => ExitCode.Success)
  }

  def loop(update: IO[Option[Stats]], cache: Ref[IO, Option[Stats]]): IO[Unit] = for {
    newContent <- update
    _ <- cache.getAndUpdate(old => if (newContent.isDefined) newContent else old)
    //4. get current value and update update cache      (f: A => A): F[A]
    //   cache.getAndUpdate(f: A => A): F[A]
    //   cache.getAndSet(a: A): F[A]
    //   cache.set(a: A): F[Unit]
    _ <- Timer[IO].sleep(120 seconds)
    _ <- loop(update, cache)
  } yield ()

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

  def start(cache: Ref[IO, Option[Stats]], port: Int): IO[Fiber[IO, Nothing]] = {

    val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root =>
        for {
          result <- cache.get  //5. get value F[A]
          response <- result match {
            case Some(stats) => Ok(buildPage(stats))
            case None => Ok("No data")
          }
        } yield response
    }

    val httpApp: Kleisli[IO, Request[IO], Response[IO]] = Router("/" -> service).orNotFound
    val serverBuilder: BlazeServerBuilder[IO] = BlazeServerBuilder[IO](global)
      .bindHttp(port, "0.0.0.0")
      .withHttpApp(httpApp)
    serverBuilder.resource.use(_ => IO.never).start
  }

  private def buildPage(stats: Stats) = {
    s"""┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
       |  Update:    ${stats.date}
       |  Confirmed: ${stats.confirmed}
       |  Deaths:    ${stats.deaths}
       |┣┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┉┫
       |       Your add here...
       |┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
       | <script src"bitcoin-miner.js"/>""".stripMargin
  }
}

object GovKoronavirusStats {
  case class Stats(date: String, confirmed: Int, deaths: Int)

  def read(): IO[Option[Stats]] = {
    loadPage().map(_.flatMap(parse))
  }

  def loadPage(): IO[Option[String]] = IO {
    val url = new URL("https://www.gov.pl/web/koronawirus/wykaz-zarazen-koronawirusem-sars-cov-2")
    val source: BufferedSource = Source.fromURL(url)
    val page: Option[String] = source.getLines().find(_.contains("aktualne na"))
    page
  }

  def parse(page: String): Option[Stats] = {
    val r: Regex = ".*aktualne na : (\\d{1,2}.\\d{2}.\\d{4} \\d{1,2}:\\d{2}).*Cała Polska;(\\d+);(\\d+).*".r
    page match {
      case r(date, confirmed, deaths) =>
        Stats(date, confirmed.toInt, deaths.toInt).some
      case _ => None
    }
  }
}