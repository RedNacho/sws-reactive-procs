package sws.reactiveprocs.reactivestreams

import org.reactivestreams.Publisher
import org.reactivestreams.tck.{PublisherVerification, TestEnvironment}
import sws.reactiveprocs.ReactiveProcs.Done

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * Created by simonwhite on 12/16/17.
  */
class ReactiveProcsPublisherTest extends PublisherVerification[Long](new TestEnvironment()) {

  override def createPublisher(elements: Long): Publisher[Long] = {
    ReactiveProcsPublisher[Long](
      algorithmFactory = () => (yieldReturn: (Long) => Future[Done.type], _: () => Future[Done.type]) => {
        def yieldAll(sent: Long, remaining: Long): Future[Done.type] = {
          remaining match {
            case 0 => Future.successful(Done)
            case _ =>
              yieldReturn(sent).flatMap(_ => yieldAll(sent + 1, remaining - 1))
          }
        }

        yieldAll(0, elements)
      },
      lookAhead = 10)
  }

  override def createFailedPublisher(): Publisher[Long] = {
    new ReactiveProcsPublisher[Long](() => throw new RuntimeException("Failure"), 10)
  }

}
