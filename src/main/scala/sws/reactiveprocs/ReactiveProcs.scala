package sws.reactiveprocs

import java.util.concurrent.atomic.AtomicReference
import java.util.function.UnaryOperator

import scala.annotation.tailrec
import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * Created by simonwhite on 11/28/17.
  */
object ReactiveProcs extends App {

  /**
    * Represents a procedural algorithm which emits data to be streamed.
    * @tparam T
    */
  trait Algorithm[T] {
    /**
      * Runs the algorithm.
      * @param yieldReturn Accepts a data element from the algorithm, and returns a future which completes when that element
      *                    has been requested. Subsequent code should be hung on the returned future (the Scala async library
      *                    is the best way to achieve this effect and keep the procedural style). This is not strictly a
      *                    requirement, however - unlike C#'s yield return, you can wait for the future later.
      * @param yieldBreak  Indicates that no more data will be returned. If you wait for this future, it will never complete,
      *                    and the rest of your code will not execute. This breaks the connection between the caller and the
      *                    algorithm - any further calls to yieldReturn and yieldBreak will have no effect, and will not complete.
      * @return
      */
    def apply(yieldReturn: T => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type]
  }

  case object Done

  /**
    * Creates a Scala Stream from the supplied algorithm.
    * The Stream is non-blocking - it's up to you to control the parallelism.
    * If you simply pass it to e.g. .foreach(...), it will make requests continually, until your computer blows up.
    * Future.foldLeft is an example of a safe way to use it - this will ensure that the stream elements are requested
    * one at a time. Akka's mapAsync is the intended use case.
    *
    * If more requests have been made than there are data elements, the trailing Futures will complete with None. This
    * is why the future result is an Option. Once the algorithm signals completion, there will be no more elements.
    * @param algorithm
    * @param ec
    * @tparam T
    * @return
    */
  def stream[T](algorithm: Algorithm[T])(implicit ec: ExecutionContext): Stream[Future[Option[T]]] = {
    case class Response(requested: Promise[Done.type], response: Try[Option[T]])
    case class Request(response: Promise[Option[T]])
    case class State(unprocessedChanges: Boolean, terminationState: Option[Try[Done.type]], requestQueue: Queue[Request], responseQueue: Queue[Response])

    val state = new AtomicReference[State](State(
      unprocessedChanges = false,
      terminationState = None,
      requestQueue = Queue(),
      responseQueue = Queue()))

    // Updates the internal state of the stream in a thread-safe way.
    def updateState(transition: State => State): State = {
      state.updateAndGet(new UnaryOperator[State] {
        override def apply(t: State): State = {
          transition(t)
        }
      })
    }

    // Pushes a request for a stream element.
    def pushRequest(request: Request): Unit = {
      updateState(state => {
        state.copy(
          unprocessedChanges = true,
          requestQueue = state.requestQueue.enqueue(request))
      })

      processChanges()
    }

    // Pushes a response from the algorithm.
    def pushResponse(response: Response): Unit = {
      updateState(state => {
        val terminationState = state.terminationState.orElse(
          if (response.response.toOption.flatten.isEmpty) {
            Some(response.response.map(_ => Done))
          } else {
            None
          })

        state.copy(
          unprocessedChanges = true,
          responseQueue = if (terminationState.isDefined) {
            state.responseQueue
          } else {
            state.responseQueue.enqueue(response)
          },
          terminationState = terminationState)
      })

      processChanges()
    }

    // Processes the current state.
    // - If there is a request and a response in the queue, zips them together, allowing everybody to get on with their stuff.
    // - If we're in a termination state, keeps both queues moving independently of one another - we don't care anymore.
    // - If any changes were made while processing, recurses in case there is more work to do.
    @tailrec
    def processChanges(): Unit = {
      val updatedState = updateState(state => {
        val nextRequest = state.requestQueue.headOption
        val nextResponse = state.responseQueue.headOption

        (state.terminationState, nextRequest, nextResponse) match {
          case (_, Some(req), Some(res)) =>
            req.response.tryComplete(res.response)
            res.requested.tryComplete(res.response.map(_ => Done))
            state.copy(unprocessedChanges = true, requestQueue = state.requestQueue.tail, responseQueue = state.responseQueue.tail)
          case (Some(_), None, Some(_)) =>
            state.copy(unprocessedChanges = true, responseQueue = state.responseQueue.tail)
          case (Some(terminationState), Some(req), None) =>
            req.response.tryComplete(terminationState.map(_ => None))
            state.copy(unprocessedChanges = true, requestQueue = state.requestQueue.tail)
          case _ =>
            state.copy(unprocessedChanges = false)
        }
      })

      if (updatedState.unprocessedChanges) {
        processChanges()
      }
    }

    def responseHandler(value: Option[T]): Future[Done.type] = {
      val response = Response(Promise[Done.type](), Success(value))
      pushResponse(response)
      response.requested.future
    }

    // Starts the algorithm.
    // Handling synchronous errors, then mapping all results into a response for the stream.
    Try(algorithm(result => responseHandler(Some(result)), () => responseHandler(None)))
      .recover { case t => Future.failed(t) }
      .get
      .map(_ => Success(None))
      .recover { case t => Failure(t) }
      .foreach(f => {
        pushResponse(Response(Promise[Done.type](), f))
      })

    // Starts the Scala Stream.
    Stream.continually(
      () => {
        val request = Request(Promise[Option[T]]())

        pushRequest(request)

        request.response.future.value match {
          case Some(Success(None)) => None
          case _ => Some(request.response.future)
        }
      }
    ).map(f => f())
      .takeWhile(_.isDefined)
      .map(_.get)
  }
}