package sws.reactiveprocs

import org.scalatest.{GivenWhenThen, Matchers, WordSpec}
import sws.reactiveprocs.ReactiveProcs.{Algorithm, Done}

import scala.collection.immutable.IndexedSeq
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

/**
  * Created by simonwhite on 12/2/17.
  */
class ReactiveProcsSpecification extends WordSpec with GivenWhenThen with Matchers {

  "ReactiveProcs" should {
    "Return the elements which are generated by the algorithm" in new ReactiveProcsTestScope {
      Given("An algorithm which generates some elements")
      val promises: Stream[Promise[Element]] = Stream("A", "B", "C").map(Element).map(Promise.successful)
      val algorithm: Algorithm[Element] = createAlgorithm(promises, Promise.successful(Done))

      When("The algorithm is streamed")
      val stream: Stream[Future[Option[Element]]] = ReactiveProcs.stream(algorithm)

      Then("The non-empty stream futures contain the original elements")
      stream
        .flatMap(Await.result(_, 10.seconds))
        .map(_.value)
        .toList should be (List("A", "B", "C"))
    }

    "Return None for requests which lie beyond the end of the data" in new ReactiveProcsTestScope {
      Given("An algorithm which generates some elements, but does not complete immediately")
      val promises: Stream[Promise[Element]] = Stream("A", "B", "C").map(Element).map(Promise.successful)
      val breakPromise: Promise[Done.type] = Promise[Done.type]()
      val algorithm: Algorithm[Element] = createAlgorithm(promises, breakPromise)

      When("The algorithm is streamed")
      val stream: Stream[Future[Option[Element]]] = ReactiveProcs.stream(algorithm)

      And("We create an iterator from the stream")
      val iterator: Iterator[Future[Option[Element]]] = stream.iterator

      And("We read too many elements from the iterator before we know that the algorithm has finished")
      val futures: IndexedSeq[Future[Option[Element]]] = (1 to 10).map(_ => iterator.next())

      And("We later signal that the data has finished")
      breakPromise.success(Done)

      Then("The first three futures contain the results")
      futures
        .map(Await.result(_, 10.seconds))
        .take(3)
        .map(_.map(_.value))
        .toList should be (List(Some("A"), Some("B"), Some("C")))

      And("The remaining futures all contain None")
      futures
        .map(Await.result(_, 10.seconds))
        .drop(3)
        .distinct
        .toList should be (List(None))

      And("The iterator should not signal any more data")
      iterator.hasNext should be (false)
    }

    "Only require elements to be generated as they are being requested" in new ReactiveProcsTestScope {
      Given("An algorithm which generates some elements with a delay")
      val promises = (1 to 10).map(_ => Promise[Element]())
      val algorithm: Algorithm[Element] = createAlgorithm(promises.toStream, Promise.successful(Done))

      And("We have a buffer to track the elements in the stream")
      val readElements = new mutable.ListBuffer[Future[Option[Element]]]()

      When("The algorithm is streamed to the buffer")
      val stream: Stream[Future[Option[Element]]] = ReactiveProcs.stream(algorithm)
        .map(f => { readElements += f; f })

      And("We create an iterator from the stream")
      val iterator: Iterator[Future[Option[Element]]] = stream.toIterator

      And("We only return the first element")
      promises(0).success(Element("first"))

      And("We read the first element from the iterator")
      val firstElement: Future[Option[Element]] = iterator.next()

      Then("The first element has the correct value")
      Await.result(firstElement, 10.seconds) should be (Some(Element("first")))

      When("We return the second element")
      promises(1).success(Element("second"))

      And("We read the second element from the iterator")
      val secondElement: Future[Option[Element]] = iterator.next()

      Then("The second element has the correct value")
      Await.result(secondElement, 10.seconds) should be (Some(Element("second")))
    }
  }

  case class Element(value: String)

  trait ReactiveProcsTestScope {
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
  }
}
