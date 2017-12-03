package sws.reactiveprocs.examples

import java.util.concurrent.Executors

import sws.reactiveprocs.ReactiveProcs._

import scala.async.Async._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * Created by simonwhite on 12/2/17.
  */
object YieldBreakExample extends App {

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  // Modification of the basic example which yield breaks when i == 500.
  object MyAlgorithm extends Algorithm[String] {
    override def apply(yieldReturn: (String) => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type] = {
      async {
        var i = 0
        while (i < 1000) {
          await(yieldReturn(i.toString))

          // For some reason "if" isn't working with async, hacked it with while...
          while (i == 500) {
            await(yieldBreak())
            throw new RuntimeException("We shouldn't have reached this point!")
          }

          i += 1
        }

        Done
      }
    }
  }

  // Deliberately request too many elements up front, to prove that we stop at 500.
  val iterator = stream(MyAlgorithm).iterator

  val futures = (1 to 1000).flatMap(_ => if (iterator.hasNext) {
    Some(iterator.next)
  } else {
    None
  })

  val results = Future.foldLeft(futures)(()) {
    case (_, Some(next)) =>
      println(next)
    case _ =>
      println("Future completed with None; we must have run out of data.")
  }

  Await.result(results, 10.seconds)

  System.exit(0)
}