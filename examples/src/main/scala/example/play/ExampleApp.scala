package example.play

import akka.actor.ActorSystem
import example.{ ExampleApi, ExampleService }
import example.ExampleData.sampleCharacters
import example.ExampleService.ExampleService
import caliban.PlayAdapter
import play.api.Mode
import play.api.routing._
import play.api.routing.sird._
import play.core.server.{ AkkaHttpServer, ServerConfig }
import sttp.tapir.json.play._
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform
import zio.Runtime

import scala.io.StdIn.readLine
import zio.blocking.Blocking
import zio.random.Random

import scala.concurrent.ExecutionContextExecutor

object ExampleApp extends App {

  implicit val system: ActorSystem                                                                = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor                                         = system.dispatcher
  implicit val runtime: Runtime[ExampleService with Console with Clock with Blocking with Random] =
    Runtime.unsafeFromLayer(
      ExampleService.make(sampleCharacters) ++ Console.live ++ Clock.live ++ Random.live ++ Blocking.live,
      Platform.default
    )

  val interpreter = runtime.unsafeRun(ExampleApi.api.interpreter)

  val server = AkkaHttpServer.fromRouterWithComponents(
    ServerConfig(
      mode = Mode.Dev,
      port = Some(8088),
      address = "127.0.0.1"
    )
  ) { _ =>
    Router.from {
      case req @ POST(p"/api/graphql") => PlayAdapter.makeHttpService(interpreter).apply(req)
      case req @ GET(p"/ws/graphql")   => PlayAdapter.makeWebSocketService(interpreter).apply(req)
    }.routes
  }

  println("Server online at http://localhost:8088/\nPress RETURN to stop...")
  readLine()
  server.stop()

}
