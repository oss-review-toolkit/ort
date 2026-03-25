package com.example.http4stemplate

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  def run(args: List[String]) =
    Http4stemplateServer.stream[IO].compile.drain.as(ExitCode.Success)
}
