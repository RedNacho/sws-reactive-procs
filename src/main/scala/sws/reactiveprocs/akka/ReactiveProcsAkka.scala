package sws.reactiveprocs.akka

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Source}
import sws.reactiveprocs.ReactiveProcs._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by simonwhite on 12/2/17.
  */
object ReactiveProcsAkka {

  implicit class SourceExtensions[S](source: Source[S, _]) {

    /**
      * Executes the supplied algorithm on each element, outputting the results as individual elements.
      * @param parallelism The number of requests to the stream which can be in flight at one time.
      * @param algorithmFactory Factory which creates the algorithm to execute against the input.
      * @param ec
      * @tparam T
      * @return
      */
    def flatMapThroughAlgorithm[T](parallelism: Int = 1)(algorithmFactory: S => Algorithm[T])(implicit ec: ExecutionContext): Source[T, _] = {
      source.flatMapConcat(s => {
        ReactiveProcsAkka.source(parallelism)(() => algorithmFactory(s))
      })
    }

  }

  /**
    * Creates a data source based on the supplied algorithm.
    * @param parallelism The number of requests to the stream which can be in flight at one time.
    * @param algorithmFactory Factory which creates the algorithm each time the source is materialised.
    * @param ec
    * @tparam T
    * @return
    */
  def source[T](parallelism: Int = 1)(algorithmFactory: () => Algorithm[T])(implicit ec: ExecutionContext): Source[T, NotUsed] = {
    Source.fromIterator(() => {
      stream(algorithmFactory()).iterator
    }).via(futureResultsFlow(parallelism))
  }

  private [this] def futureResultsFlow[T](parallelism: Int) = {
    Flow[Future[Option[T]]]
      .mapAsync(parallelism)(f => f)
      .takeWhile(_.isDefined)
      .map(_.get)
  }
}
