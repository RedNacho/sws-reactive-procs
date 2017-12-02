package sws.reactiveprocs.examples

import java.util.concurrent.Executors
import sws.reactiveprocs.ReactiveProcs._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.async.Async._

/**
  * Created by simonwhite on 12/2/17.
  */
object BasicAsyncExample extends App {

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  // Basic algorithm which emits some values and uses Scala async to
  // yield control to the calling code.
  object MyAlgorithm extends Algorithm[String] {
    override def apply(yieldReturn: (String) => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type] = {
      async {
        var i = 0
        while (i < 1000) {
          await(yieldReturn(i.toString))
          i += 1
        }

        Done
      }
    }
  }

  // It's safe to use foldLeft, because the futures are evaluated sequentially.
  val results = Future
    .foldLeft(stream[String](MyAlgorithm))(List[Option[String]]()) { case (current, next) =>
      next.foreach(n => println(n))
      next::current
    }

  Await.result(results, 10.seconds)

  System.exit(0)
}
