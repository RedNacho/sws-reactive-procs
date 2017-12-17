package sws.reactiveprocs.reactivestreams

import org.reactivestreams.Publisher
import sws.reactiveprocs.ReactiveProcs
import sws.reactiveprocs.ReactiveProcs.Algorithm
import sws.reactiveprocs.reactivestreams.internals.FutureStreamPublisher

import scala.concurrent.ExecutionContext

object ReactiveProcsPublisher {
  /**
    * DANGER! DANGER! May contain nuts. And bugs.
    *
    * Creates a Reactive Streams publisher based on the supplied algorithm.
    *
    * DISCLAIMER: Implementing Reactive Streams from scratch is not trivial. I may not have done it right.
    * Or I might've written something that performs crappily.
    * I highly recommend using the Akka or RxScala implementations instead (or rolling your own based on these).
    * @param algorithmFactory Creates an instance of the algorithm. Will be called for each subscription.
    * @param lookAhead The number of results to read ahead. This will behave more or less like Akka's mapAsync parallelism, since there will always be this many requests in progress.
    *                  This can be very powerful in the context of ReactiveProcs
    * @param executionContext Context in which asynchronous callbacks will run.
    * @tparam T The type of the stream elements being published.
    */
  def apply[T](algorithmFactory: () => Algorithm[T], lookAhead: Int = 0)
              (implicit executionContext: ExecutionContext): Publisher[T] = {
    new ReactiveProcsPublisher(algorithmFactory, lookAhead)
  }
}

private class ReactiveProcsPublisher[T](algorithmFactory: () => Algorithm[T], lookAhead: Int)(implicit executionContext: ExecutionContext) extends FutureStreamPublisher[T](
  streamFactory = () => ReactiveProcs.stream(algorithmFactory()),
  lookAhead = lookAhead
)