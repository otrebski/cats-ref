package server

import cats.data.Kleisli
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, Fiber, IO, Timer}

object HttpServer {

  import org.http4s._
  import org.http4s.dsl.io._
  import org.http4s.implicits._
  import org.http4s.server.Router
  import org.http4s.server.blaze._

  import scala.concurrent.ExecutionContext.Implicits.global

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
