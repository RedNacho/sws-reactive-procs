package sws.reactiveprocs.examples

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.Supervision.Directive
import akka.stream.scaladsl.{Keep, Sink, Source}
import sws.reactiveprocs.ReactiveProcs.{Algorithm, Done}
import sws.reactiveprocs.reactivestreams.ReactiveProcsPublisher

import scala.async.Async.{async, await}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * DISCLAIMER: Don't use this publisher! Use Akka/RxJava/something else instead.
  */
object BasicPublisherExample extends App {

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  // This algorithm doesn't terminate, but it won't blow up the universe either, because it yields control to Akka.
  object MyAlgorithm extends Algorithm[String] {
    override def apply(yieldReturn: (String) => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type] = {
      async {
        var i = 0
        while (true) {
          await { yieldReturn(i.toString) }
          i += 1
        }

        Done
      }
    }
  }

  implicit val actorSystem = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer(ActorMaterializerSettings(actorSystem).withSupervisionStrategy(
    new Supervision.Decider {
      override def apply(t: Throwable): Directive = {
        t.printStackTrace()
        Supervision.stop
      }
    }))

  val result = Source.fromPublisher(ReactiveProcsPublisher(() => MyAlgorithm, lookAhead = 10))
    .toMat(Sink.foreach(v => println(v)))(Keep.right)
    .run

  Await.result(result, 5.minutes)

  System.exit(0)
}
