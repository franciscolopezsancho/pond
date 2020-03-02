organization in ThisBuild := "com.lightbend"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % Test

lazy val `pond` = (project in file("."))
  .aggregate(`pond-api`, `pond-impl`, `pond-journal-reader`)

lazy val `pond-api` = (project in file("pond-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `pond-impl` = (project in file("pond-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`pond-api`)

lazy val `pond-journal-reader` = (project in file("pond-journal-reader"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-persistence-query" % "2.5.23",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.5.23",
      "com.typesafe.akka" %% "akka-stream" % "2.5.23",
      "com.typesafe.akka" %% "akka-persistence-cassandra" % "0.99"
    )
  )

lagomCassandraPort in ThisBuild := 9042

