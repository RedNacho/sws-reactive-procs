package sws.reactiveprocs.reactivestreams.internals

import java.util
import java.util.Collections
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import sws.reactiveprocs.ReactiveProcs.Done

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * TODO This is a more generic component than ReactiveProcsPublisher, and should be tested separately if used separately.
  * TODO It's also kind of complicated. Reactive Streams is hard... I've put some comments in for now.
  */
private [reactivestreams] class FutureStreamPublisher[T](streamFactory: () => Stream[Future[Option[T]]], lookAhead: Int)(implicit executionContext: ExecutionContext) extends Publisher[T] {

  // Ensuring that an error is thrown if we see the same subscriber twice.
  private [this] val seen = Collections.synchronizedMap(new util.WeakHashMap[Subscriber[_ >: T], Boolean]())

  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    seen.compute(subscriber, (_: Subscriber[_ >: T], existingValue: Boolean) => {
      if (Option(existingValue).getOrElse(false)) {
        throw new IllegalStateException("Subscriber already seen (rule 1.10)!")
      }
      true
    })

    // Signals that onSubscribe has finished. Nothing should be sent to the subscriber until this completes.
    val onSubscribeCompleted = Promise[Done.type]()

    subscriber.onSubscribe(new FutureStreamSubscription(
      streamFactory,
      lookAhead,
      onSubscribeCompleted.future,
      subscriber
    ))

    onSubscribeCompleted.success(Done)
  }
}

private class FutureStreamSubscription[T](streamFactory: () => Stream[Future[Option[T]]],
                                          lookAhead: Int,
                                          onSubscribeCompleted: Future[Done.type],
                                          subscriber: => Subscriber[T])
                                         (implicit executionContext: ExecutionContext) extends Subscription {

  // Ref to subscriber; var because of rule 1.13.
  @volatile
  private [this] var subscriberOpt: Option[Subscriber[T]] = Some(subscriber)
  // Pending requests, or -1 == "effectively unbounded" (rule 1.17)
  private [this] val pendingRequests = new AtomicLong(0)
  // Subscription was cancelled or stream completed; do nothing else.
  private [this] val done: AtomicBoolean = new AtomicBoolean(false)

  // Ensures that actions on the subscriber take place sequentially.
  private [this] val sequencer = Sequencer(
    onError = t => {
      finished(Some(Failure(t)))
      Future.successful(Done)
    })

  // Ensures that no actions are made against the subscriber until the subscription has been set up.
  sequencer.enqueue { onSubscribeCompleted }

  // Kind of a customised Iterator for reading from the Stream.
  private [this] val streamBuffer = StreamBuffer(
    stream = Try(streamFactory())
      .recover { case t =>
        sequencer.enqueue {
          finished(Some(Failure(t)))
          Future.successful(Done)
        }
        Stream.empty
      }.get,
    lookAhead = lookAhead)

  // Cancels the subscription, no more messages will be sent.
  override def cancel(): Unit = {
    sequencer.enqueue { finished(None); Future.successful(Done) }
  }

  // Adds pending requests; and ensures that issueEvents runs to push stuff downstream.
  override def request(n: Long): Unit = {
    if (n <= 0) {
      sequencer.enqueue {
        finished(Some(Failure(new IllegalArgumentException("Number of requested elements must be greater than zero (rule 1.9)!"))))
        Future.successful(Done)
      }
    } else {
      addPendingRequests(n)
      sequencer.enqueue { issueEvents() }
    }
  }

  // Atomically adds pending requests to the counter.
  @tailrec
  private [this] def addPendingRequests(n: Long): Unit = {
    val current = pendingRequests.get()

    val next = if (current == -1 || Long.MaxValue - n < current) {
      -1 // = "effectively unbounded"
    } else {
      current + n
    }

    if (!pendingRequests.compareAndSet(current, next)) {
      addPendingRequests(n)
    }
  }

  // Atomically pops a pending request; returns true if one was found.
  private [this] def popPendingRequest(): Boolean = {
    if (done.get()) {
      false
    } else {
      val requestCount = pendingRequests.getAndUpdate(i => {
        if (i > 0) i - 1 else i
      })
      requestCount != 0
    }
  }

  // Thread-free version of an "event loop".
  // Will recurse as long as there are requests to respond to and elements to send.
  // Is retriggered every time new requests come in.
  // This can potentially run for the entire lifetime of the stream (if the subscriber isn't waiting for responses
  // before making more requests), or it can potentially run once for every request (if the subscriber is only
  // making requests after it receives everything).
  private [this] def issueEvents(): Future[Done.type] = {
    if (popPendingRequest()) {
      streamBuffer.nextOption
        .getOrElse(Future.successful(None))
        .flatMap {
          case Some(e) =>
            sendElement(e)
            issueEvents()
          case _ =>
            finished(Some(Success(Done)))
            Future.successful(Done)
        }.recover {
        case t =>
          finished(Some(Failure(t)))
          Done
      }
    } else {
      Future.successful(Done)
    }
  }

  // Puts us into the "done" state. Guaranteed to run once.
  // If signal is None: THe subscription was cancelled, we don't send anything downstream.
  // If signal is Success: The stream completed successfully; send onComplete.
  // If signal is Failure: An error occurred; send it.
  private [this] def finished(signal: Option[Try[Done.type]]): Unit = {
    if (!done.getAndSet(true)) {
      signal match {
        case Some(Success(_)) => subscriberOpt.get.onComplete()
        case Some(Failure(t)) => subscriberOpt.get.onError(t)
        case _ =>
      }
      subscriberOpt = None // Rule 1.13.
    }
  }

  // Sends an element, unless we're in the "done" state.
  private [this] def sendElement(element: T): Unit = {
    if (!done.get()) {
      subscriberOpt.get.onNext(element)
    }
  }
}