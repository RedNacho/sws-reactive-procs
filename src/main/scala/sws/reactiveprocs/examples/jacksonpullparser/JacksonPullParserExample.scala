package sws.reactiveprocs.examples.jacksonpullparser

import java.nio.file.FileSystems
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.Supervision.Directive
import akka.stream.scaladsl.{FileIO, Keep, Sink, StreamConverters}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import sws.reactiveprocs.akka.ReactiveProcsAkka

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import spray.json._

/**
  * Created by simonwhite on 12/2/17.
  */
object JacksonPullParserExample extends App {

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  // Generate some data.
  TestDataGenerator("data")

  // Akka boilerplate.
  implicit val actorSystem = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(actorSystem).withSupervisionStrategy(
    new Supervision.Decider {
      override def apply(t: Throwable): Directive = {
        t.printStackTrace()
        Supervision.stop
      }
    }))

  // Parse the data with Akka and spit it out with Spray.
  val objectMapper = new ObjectMapper()

  val streamResult = ReactiveProcsAkka.source(10)(() => {
    val dataSource = FileIO.fromPath(FileSystems.getDefault.getPath("data"))
      .runWith(StreamConverters.asInputStream(60.seconds))
    val jsonFactory = new JsonFactory()
    val parser = jsonFactory.createParser(dataSource)
    new JsonParsingAlgorithm(parser, objectMapper)
  }).map(node => objectMapper.writeValueAsString(node).parseJson)
    .toMat(Sink.foreach(v => println(v.prettyPrint)))(Keep.right)
    .run()

  Await.result(streamResult, 15.minutes)

  System.exit(0)
}
