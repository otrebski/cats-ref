package server

import cats.effect.concurrent.Ref
import cats.effect.{ExitCode, IO, IOApp, Timer}
import cats.implicits._

import scala.concurrent.duration._
import scala.language.postfixOps

case class Stats(date: String, confirmed: Int, deaths: Int)

object ServerApp extends IOApp {

  def loop(update: IO[Option[Stats]], cache: Ref[IO, Option[Stats]]): IO[Unit] = for {
    _ <- IO(println("Updating..."))
    newContent <- update
    _ <- cache.getAndUpdate(old =>
      (old, newContent) match {
        case (_, None) => println("There is no new value, will use old one"); old
        case (Some(prev), Some(newValue)) if prev == newValue =>
          println("There is no change")
          old
        case (_, Some(newValue)) =>
          println(s"Value is updated $old -> $newValue")
          newValue.some
      }
    )
    _ <- Timer[IO].sleep(10 seconds)
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




