#sws-reactive-procs

Not really a library, it's too small... But feel free to steal the code.

**The problem**

1. C#'s yield return/break construct doesn't exist in Scala, and I miss it.
2. I was recently working on an Akka Streams project involving JSON parsing, and I realised that what I really wanted to do was use a procedural algorithm (involving the Jackson pull parser) in a reactive way.

Essentially, I wanted to be able to write really procedural code like this (NOT a working example):

```
  def runAlgorithm(): Stream[String] = {
    var x = 1

    while (x <= 3) {
      yield return x.toString
      x += 1
    }
    
    yield return "Wait, I'm not done yet."
    
    var y = 4

    while (y <= 6) {
      yield return y.toString
      y += 1
    }
  }
  
  runAlgorithm().foldLeft(()) {
    case (_, next) => {
      println(next)
  }}
```

MSDN can explain yield return in C# better than I can, but it's basically a continuation feature for lazily evaluated collections (IEnumerable in .Net). The code executes until it hits the yield return statement, when control is returned to the caller. The caller then does whatever it likes until it needs the next element in the collection, whereupon the code continues running.

This would be especially useful with Akka Streams or another Reactive Streams library.

**My solution**

Implement a thing which is able to take a procedural algorithm generating data and adapt it to a Scala Stream, ensuring that the algorithm only proceeds as elements are requested from it (thus creating backpressure).

The basic code is pure Scala/Java, so you don't need any dependencies to use it - although it is built with the Scala async library in mind (see build.sbt). I chose not to use Scala continuations because I read that this has been deprecated, and in any case I didn't want to create a plugin dependency.

It's built on futures, but it does not block and only requires a minimal execution context - all of the examples use a single thread.

The above code can be written like this, using the Async library:

```
  import sws.reactiveprocs.ReactiveProcs._
  import scala.async.Async._

  // Fairly close to the original "runAlgorithm", with a bit of additional boilerplate.
  def runAlgorithm(yieldReturn: (String) => Future[Done.type], yieldBreak: () => Future[Done.type]) = async {
    var x = 1

    while (x <= 3) {
      await { yieldReturn(x.toString) }
      x += 1
    }

    await { yieldReturn("Wait, I'm not done yet.") }

    var y = 4

    while (y <= 6) {
      await { yieldReturn(y.toString) }
      y += 1
    }

    Done
  }

  // Slightly more code required to process the output, since we get Futures.
  val streamCompletedFuture = Future.foldLeft(stream(runAlgorithm))(()) {
    case (_, Some(result)) =>
      println(result)
    case _ =>
  }

  Await.result(streamCompletedFuture, 10.seconds)
```

You can also integrate the algorithm with Akka Streams, which is what I wanted to do:

```
  import sws.reactiveprocs.akka.ReactiveProcsAkka._
  import akka.stream.scaladsl._

  val streamCompletedFuture = source[String](10)(() => runAlgorithm)
    .toMat(Sink.foreach(println))(Keep.right)
    .run

  Await.result(streamCompletedFuture, 10.seconds)
```

Also, you can write the algorithm without the async library if you want to handle the futures directly (this is not as dumb as it sounds, because async does not support functional constructs - including for loops):

```
  import sws.reactiveprocs.ReactiveProcs._

  def runAlgorithm(yieldReturn: (String) => Future[Done.type], yieldBreak: () => Future[Done.type]) = {
    for {
      _ <- Future.sequence(
        (1 to 3).toStream.map(x =>
          yieldReturn(x.toString))
      )

      _ <- yieldReturn("Wait, I'm not done yet.")

      _ <- Future.sequence(
        (4 to 6).toStream.map(y =>
          yieldReturn(y.toString))
      )
    } yield Done
  }
```

See the examples package for stuff you can run and play with.

**Important note about backpressure**

Backpressure is a supported feature of this implementation, but it is NOT automatic.

If you want your algorithm to wait for a result to be returned before continuing, you have to wait for the yieldReturn future yourself (Scala async is one way to do this).

Similarly, if you want your Stream not to request the next element until the previous Future has completed, you have to handle this yourself (e.g. with Akka's mapAsync, Future.foldLeft, etc). If you just use the Stream without waiting for any of the Futures, it will just keep requesting indefinitely, which is probably not what you want.

**How it works**

There isn't a lot of code, but it bears some explanation.

There is a request queue (which stores requests for data coming from the user of the Scala Stream), and a response queue (which stores output from the algorithm).

A request includes a Promise which is completed when a result is available, and a response includes a Promise which is completed when the response is requested.

When we have both a request and a response available, we marry them together, completing both Promises. This tells the algorithm to continue moving forward, and it gives the user of the Stream a result, telling them to move forward as well.

So, the user of the Stream can queue up requests faster than the responses arrive, and the algorithm can queue up responses faster than the requests arrive. BUT, if the user of the Stream is smart, they will only wait for a limited number of Futures at one time (e.g. with Future.foldLeft or Akka's mapAsync parallelism), allowing them to be backpressured. And if the algorithm is smart, it will only compute responses as they are being requested (e.g. with Scala async), allowing it to also be backpressured.