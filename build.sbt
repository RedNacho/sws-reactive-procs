name := "sws-reactive-procs"

version := "1.0"

scalaVersion := "2.12.4"

//Test dependencies
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"

//Dependencies required for Akka Streams use.
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.7"

//Dependencies required for RxScala use.
libraryDependencies += "io.reactivex" % "rxscala_2.12" % "0.26.5"

//Dependencies required for Scala async use.
libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.6"

//Dependencies required for the Jackson example.
libraryDependencies += "io.spray" %%  "spray-json" % "1.3.3"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.9.2"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.2"