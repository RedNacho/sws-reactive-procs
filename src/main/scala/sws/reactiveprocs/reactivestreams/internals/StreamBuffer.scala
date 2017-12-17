package sws.reactiveprocs.reactivestreams.internals

import java.util.concurrent.locks.ReentrantLock

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.util.Try

private object StreamBuffer {
  def apply[T](streamFactory: () => Stream[T], lookAhead: Int): StreamBuffer[T] = {
    new StreamBuffer(streamFactory, lookAhead)
  }
}

/**
  * Buffers Stream data in memory, up to the specified lookAhead.
  * Did not implement Iterator interface because having a single method call is more efficient and thread-safe than hasNext/next.
  * @param streamFactory The Stream to buffer.
  * @param lookAhead The number of elements to load in advance.
  *                  If 0: The next stream element will not be requested until nextOption is invoked.
  *                  If 1: The next stream element will be requested by the previous nextOption call. A single element will be loaded on init.
  *                  n > 1: n elements will be loaded on init, a new one will be requested as nextOption is called.
  * @tparam T The type of the buffer elements
  */
private class StreamBuffer[T](streamFactory: () => Stream[T], lookAhead: Int) {
  // TODO Figure out a non-locky way of interacting with the Stream. I've used an explicit ReentrantLock to remind myself of this.
  private [this] val lock = new ReentrantLock()

  private [this] var queue: Queue[T] = Queue()

  private [this] val iterator = {
    lock.lock()
    val it = Try {
      val it = streamFactory().iterator
      fillQueue(it, lookAhead)
      it
    }
    lock.unlock()
    it.get
  }

  def nextOption: Option[T] = {
    lock.lock()

    val result = Try {
      fillQueue(iterator, 1)
      val dequeueOpt = queue.dequeueOption
      queue = dequeueOpt.map { case (_, q) => q }.getOrElse(Queue.empty)
      fillQueue(iterator, lookAhead)
      dequeueOpt.map { case (r, _) => r }
    }

    lock.unlock()

    result.get
  }

  @tailrec
  private [this] def fillQueue(iterator: Iterator[T], upTo: Int): Unit = {
    if (queue.size < upTo && iterator.hasNext) {
      queue = queue.enqueue(iterator.next())
      fillQueue(iterator, upTo)
    }
  }
}
