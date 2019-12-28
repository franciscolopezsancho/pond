package com.lightbend.pond.impl

import com.lightbend.pond.api
import com.lightbend.pond.api.PondService
import akka.Done
import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityRef
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.EventStreamElement
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.transport.BadRequest

/**
  * Implementation of the PondService.
  */
class PondServiceImpl(
  clusterSharding: ClusterSharding,
  persistentEntityRegistry: PersistentEntityRegistry
)(implicit ec: ExecutionContext)
  extends PondService {

  /**
    * Looks up the entity for the given ID.
    */
  private def entityRef(id: String): EntityRef[PondCommand] =
    clusterSharding.entityRefFor(PondState.typeKey, id)

  implicit val timeout = Timeout(5.seconds)

  override def hello(id: String): ServiceCall[NotUsed, String] = ServiceCall {
    _ =>
      // Look up the sharded entity (aka the aggregate instance) for the given ID.
      val ref = entityRef(id)

      // Ask the aggregate instance the Hello command.
      ref
        .ask[Greeting](replyTo => Hello(id, replyTo))
        .map(greeting => greeting.message)
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the sharded entity (aka the aggregate instance) for the given ID.
    val ref = entityRef(id)

    // Tell the aggregate to use the greeting message specified.
    ref
      .ask[Confirmation](
        replyTo => UseGreetingMessage(request.message, replyTo)
      )
      .map {
        case Accepted => Done
        case _        => throw BadRequest("Can't upgrade the greeting message.")
      }
  }

  override def greetingsTopic(): Topic[api.GreetingMessageChanged] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(PondEvent.Tag, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
    }

  private def convertEvent(
    helloEvent: EventStreamElement[PondEvent]
  ): api.GreetingMessageChanged = {
    helloEvent.event match {
      case GreetingMessageChanged(msg) =>
        api.GreetingMessageChanged(helloEvent.entityId, msg)
    }
  }
}
