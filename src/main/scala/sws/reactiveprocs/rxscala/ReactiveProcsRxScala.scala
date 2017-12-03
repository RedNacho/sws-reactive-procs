package sws.reactiveprocs.rxscala

import rx.lang.scala.Observable
import sws.reactiveprocs.ReactiveProcs._

import scala.concurrent.ExecutionContext

/**
  * Created by simonwhite on 12/3/17.
  *
  * Pretty much the same API as ReactiveProcsAkka.
  */
object ReactiveProcsRxScala {

  implicit class ObservableExtensions[S](observable: Observable[S]) {

    def flatMapThroughAlgorithm[T](parallelism: Int)(algorithmFactory: S => Algorithm[T])(implicit ec: ExecutionContext): Observable[T] = {
      observable
        .flatMap(s => ReactiveProcsRxScala.observable(parallelism)(algorithmFactory(s)))
    }

  }

  def observable[T](parallelism: Int)(algorithm: Algorithm[T])(implicit ec: ExecutionContext): Observable[T] = {
    Observable.from(stream(algorithm))
      .flatMap(
        maxConcurrent = parallelism,
        f = future => Observable.from(future))
      .takeWhile(_.isDefined)
      .map(_.get)
  }
}
