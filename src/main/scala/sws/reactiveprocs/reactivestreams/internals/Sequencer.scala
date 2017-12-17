package sws.reactiveprocs.reactivestreams.internals

import java.util.concurrent.atomic.AtomicReference

import sws.reactiveprocs.ReactiveProcs.Done

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future, Promise}

private object Sequencer {
  def apply(onError: Throwable => Future[Done.type])(implicit executionContext: ExecutionContext): Sequencer = {
    new Sequencer(onError)
  }
}

/**
  * Runs async actions sequentially.
  * @param onError
  */
private class Sequencer(onError: Throwable => Future[Done.type])(implicit executionContext: ExecutionContext) {
  private [this] val nextActionTrigger = new AtomicReference[Future[Done.type]](Future.successful(Done))

  def enqueue(action: => Future[Done.type]): Unit = {
    val actionCompleted = Promise[Done.type]()
    val trigger: Future[Done.type] = advanceActionTrigger(actionCompleted.future)

    trigger
      .flatMap(_ => action)
      .map(_ => {
        actionCompleted.success(Done)
        Done
      })
      .recover {
        case t =>
          enqueue { onError(t) }
          actionCompleted.success(Done)
      }
  }

  @tailrec
  private [this] def advanceActionTrigger(actionCompleteFuture: Future[Done.type]): Future[Done.type] = {
    val after = nextActionTrigger.get()

    if (nextActionTrigger.compareAndSet(after, actionCompleteFuture)) {
      after
    } else {
      advanceActionTrigger(actionCompleteFuture)
    }
  }
}
