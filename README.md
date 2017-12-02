# sws-reactive-procs

Not really a library, it's too small... But feel free to steal the code.

The problem:

1. C#'s yield return/break construct doesn't exist in Scala, and I miss it.
2. I was recently working on an Akka Streams project involving JSON parsing, and I realised that what I really wanted to do was use a procedural algorithm (involving the Jackson pull parser) in a reactive way.

My solution:

Implement a thing which is able to take a procedural algorithm generating data and adapt it to a Scala Stream, ensuring that the algorithm only proceeds as elements are requested from it (thus creating backpressure).

The basic code is pure Scala/Java, so you don't need any dependencies to use it - although it is built with the Scala async library in mind (see build.sbt).

It's built on futures, but it does not block and only requires a minimal execution context - all of the examples use a single thread.

See the examples for usage.