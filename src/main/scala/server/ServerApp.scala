package server

import java.io.FileInputStream

import cats.data.Kleisli
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, ExitCode, Fiber, IO, IOApp, Timer}

import scala.concurrent.duration._
import scala.io.BufferedSource
import scala.language.postfixOps

object ServerApp extends IOApp {
  type FileContent = String

  def loop(update: IO[String], cache: Ref[IO, FileContent]): IO[Unit] = for {
    newContent <- update
    oldContent <- cache.get
    _ <- if (newContent != oldContent) IO(println("Content updated")) else IO.unit
    _ <- cache.set(newContent)
    _ <- Timer[IO].sleep(10 seconds)
    _ <- loop(update, cache)
  } yield ()

  override def run(args: List[String]): IO[ExitCode] = {
    val update: IO[String] = IO {
      try {
        val source = new BufferedSource(new FileInputStream("file.txt"))
        source.getLines().mkString("\n")
      } catch {
        case e: Exception => ""
      }
    }

    val program = for {
      cache <- Ref.of[IO, FileContent]("")
      fiber <- HttpServer.start(cache)
      _ <- loop(update, cache)
    } yield ()
    program.map(_ => ExitCode.Success)
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

  def start(cache: Ref[IO, String]): IO[Fiber[IO, Nothing]] = {

    val service: HttpRoutes[IO] = HttpRoutes.of[IO] {
      case GET -> Root =>
        val current: IO[Response[IO]] = for {
          result <- cache.get
          response <- Ok(result)
        } yield response
        current
    }

    val httpApp: Kleisli[IO, Request[IO], Response[IO]] = Router("/" -> service).orNotFound
    val serverBuilder: BlazeServerBuilder[IO] = BlazeServerBuilder[IO]
      .bindHttp(8090, "localhost")
      .withHttpApp(httpApp)
    serverBuilder.resource.use(_ => IO.never).start
  }
}
