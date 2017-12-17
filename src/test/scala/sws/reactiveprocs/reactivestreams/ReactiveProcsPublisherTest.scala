package sws.reactiveprocs.reactivestreams

import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import sws.reactiveprocs.ReactiveProcs.Done

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by simonwhite on 12/16/17.
  */
class ReactiveProcsPublisherTest extends PublisherVerification[Long](new TestEnvironment()) {

  override def createPublisher(elements: Long): Publisher[Long] = {
    ReactiveProcsPublisher[Long](
      algorithmFactory = () => (yieldReturn: (Long) => Future[Done.type], _: () => Future[Done.type]) => async {
        @volatile
        var sent: Long = 0

        while (sent < elements) {
          await { yieldReturn(sent) }
          sent += 1
        }

        Done
      },
      lookAhead = 10)
  }

  override def createFailedPublisher(): Publisher[Long] = {
    ReactiveProcsPublisher[Long](() => throw new RuntimeException("Failure"), 10)
  }

}
