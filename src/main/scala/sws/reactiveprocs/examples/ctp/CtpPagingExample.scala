package sws.reactiveprocs.examples.ctp

import java.util.concurrent.Executors

import io.sphere.sdk.client.{ScalaSphereClient, SphereClientFactory}
import io.sphere.sdk.products.ProductProjection
import io.sphere.sdk.products.queries.ProductProjectionQuery
import io.sphere.sdk.queries.{PagedQueryResult, QueryPredicate, QuerySort, StringQuerySortingModel}
import rx.lang.scala.Observable
import sws.reactiveprocs.ReactiveProcs.{Algorithm, Done}
import sws.reactiveprocs.rxscala.ReactiveProcsRxScala.observable

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.async.Async._

/**
  * RxScala example which queries CommerceTools Platform, paging the data.
  */
object CtpPagingExample extends App {

  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())

  val projectKey = "my-project-key"
  val clientId = "my-client-id"
  val clientSecret = "my-client-secret"

  val client = ScalaSphereClient(SphereClientFactory.of().createClient(
    projectKey,
    clientId,
    clientSecret
  ))

  object CtpPagingAlgorithm extends Algorithm[PagedQueryResult[ProductProjection]] {
    override def apply(yieldReturn: (PagedQueryResult[ProductProjection]) => Future[Done.type], yieldBreak: () => Future[Done.type]): Future[Done.type] = async {
      var lastId: Option[String] = None

      val baseQuery = ProductProjectionQuery
        .ofStaged()
        .withSort(QuerySort.of[ProductProjection]("id")) // Get the results back ordered by ID.
        .withFetchTotal(false) // Don't fetch the total (easier on the platform)

      do {
        // If we have a last ID, filter on this to get the next page.
        val query = lastId match {
          case Some(id) => baseQuery
            .withPredicates(QueryPredicate.of[ProductProjection](s"id > ${StringQuerySortingModel.normalize(id)}"))
          case _ => baseQuery
        }

        val results = await { client(query) }

        await { yieldReturn(results) }

        lastId = results.getResults.asScala.lastOption
          .map(_.getId) // Last ID in the current page, or None if page is empty.
      } while (lastId.isDefined)

      Done
    }
  }

  observable(10)(CtpPagingAlgorithm)
    .flatMap(results => Observable.from(results.getResults.asScala))
    .map(product => println(s"Found product with ID ${product.getId}"))
    .toBlocking
    .last

  System.exit(0)
}
