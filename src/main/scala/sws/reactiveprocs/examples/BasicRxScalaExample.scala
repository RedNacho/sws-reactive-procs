package sws.reactiveprocs.examples

import java.util.concurrent.Executors

import sws.reactiveprocs.ReactiveProcs._
import sws.reactiveprocs.rxscala.ReactiveProcsRxScala._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by simonwhite on 12/3/17.
  */
object BasicRxScalaExample extends App {

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

  observable(10)(MyAlgorithm)
    .map(v => println(v))
    .toBlocking
    .last

  System.exit(0)
}
