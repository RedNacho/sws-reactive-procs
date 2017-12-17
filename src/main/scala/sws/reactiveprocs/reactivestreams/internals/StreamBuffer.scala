package sws.reactiveprocs.reactivestreams.internals

import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

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
  private [this] val queue = new AtomicReference[(Queue[T], Stream[T])]((Queue.empty, streamFactory()))

  def nextOption: Option[T] = nextOptionRec
  
  @tailrec
  private [this] def nextOptionRec: Option[T] = {
    val current = fillQueue(1)
    
    current match {
      case (q, s) if q.nonEmpty =>
        val result = q.head
        if (queue.compareAndSet(current, (q.tail, s))) {
          fillQueue(lookAhead)
          Some(result)
        } else {
          nextOptionRec
        }
      case _ =>
        None
    }
  }
  
  @tailrec
  private [this] def fillQueue(upTo: Int): (Queue[T], Stream[T]) = {
    val current = queue.get()
    
    current match {
      case (q, s) if q.size < upTo && s.headOption.isDefined =>
        val next = (q.enqueue(s.head), s.tail)
        queue.compareAndSet(current, next)
        fillQueue(upTo)
      case _ =>
        current
    }
  }
}
