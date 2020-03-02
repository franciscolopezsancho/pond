package com.lightbend.pond.impl

import akka.NotUsed
import akka.actor.ActorSystem
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.{EventEnvelope, Offset, PersistenceQuery}
import akka.stream.{ActorMaterializer, scaladsl}


object JournalReader extends App {

  implicit val system = ActorSystem("QuickStart")
  implicit val mat = ActorMaterializer()

  val readJournal =
    PersistenceQuery(system).readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)
  // from AggregateEventTag
  //   override def toString: String = s"AggregateEventTag($eventType, $tag)"
  // issue query to journal
  val source: scaladsl.Source[EventEnvelope, NotUsed]  =
    readJournal.currentEventsByTag("com.lightbend.pond.impl.PondEvent", Offset.noOffset)

  // materialize stream, consuming events
  source.runForeach { event =>
    println("#################3 Event: " + event)
  }

}
