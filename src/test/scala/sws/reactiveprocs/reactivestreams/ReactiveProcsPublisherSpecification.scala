package sws.reactiveprocs.reactivestreams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import org.reactivestreams.Publisher
import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import sws.reactiveprocs.ReactiveProcs.{Algorithm, Done}

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}

/**
  * Created by simonwhite on 1/1/18.
  */
class ReactiveProcsPublisherSpecification extends WordSpec with GivenWhenThen with Matchers {

  implicit val actorSystem = ActorSystem("reactive-procs-publisher-specification")
  implicit val materializer = ActorMaterializer()

  "ReactiveProcsPublisher" should {
    "Return the elements generated by the algorithm" in new ReactiveProcsPublisherTestScope {
      Given("An algorithm which generates some elements")
      val promises: Stream[Promise[Element]] = Stream("A", "B", "C").map(Element).map(Promise.successful)
      val algorithm: Algorithm[Element] = createAlgorithm(promises, Promise.successful(Done))

      When("The algorithm is published")
      val publisher = ReactiveProcsPublisher(() => algorithm)

      Then("The published data matches the source")
      val results = getPublishedData(publisher)

      results should be (List("A", "B", "C").map(Element))
    }

    "Complete the stream with failure if something goes wrong" in new ReactiveProcsPublisherTestScope {
      Given("An algorithm which fails")
      val exception = new RuntimeException("Something is wrong")
      val algorithm: Algorithm[Element] = createAlgorithm(Stream.empty, Promise.failed(exception))

      When("The algorithm is published")
      val publisher = ReactiveProcsPublisher(() => algorithm)

      Then("The exception is sent to the subscription")
      val result = Try(getPublishedData(publisher))

      result should be (Failure(exception))
    }
  }

  case class Element(value: String)

  trait ReactiveProcsPublisherTestScope {
    def createAlgorithm(returnPromises: Stream[Promise[Element]], breakPromise: Promise[Done.type]): Algorithm[Element] = {
      (yieldReturn: (Element) => Future[Done.type], yieldBreak: () => Future[Done.type]) => {
        val futureStream: Stream[Future[Done.type]] = returnPromises
          .map(p => () => p.future)
          .map(f => f())
          .map(_.flatMap(value => yieldReturn(value))) #:::
          Stream(() => breakPromise.future)
            .map(f => f())
            .map(_.flatMap(_ => yieldBreak()))

        Future.foldLeft(futureStream)(Done) { case (_, _) => Done }
      }
    }

    def getPublishedData(publisher: Publisher[Element], elementCount: Option[Long] = None): List[Element] = {
      val source = Source.fromPublisher(publisher)
      val limiter = elementCount match {
        case None => Flow[Element]
        case Some(ec) => Flow[Element].take(ec)
      }
      val sink = Sink.fold[List[Element], Element](List()) { case (current, next) => next::current }

      Await.result(
        source
          .via(limiter)
          .toMat(sink)(Keep.right).run,
        10.seconds).reverse
    }
  }
}
