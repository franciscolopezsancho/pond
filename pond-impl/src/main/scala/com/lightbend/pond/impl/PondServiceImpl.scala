package com.lightbend.pond.impl

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRegistry}
import com.lightbend.pond.api._
import com.lightbend.pond.impl.PondState._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Implementation of the PondService.
  */
class PondServiceImpl(
                       clusterSharding: ClusterSharding,
                       persistentEntityRegistry: PersistentEntityRegistry
                     )(implicit ec: ExecutionContext)
  extends PondService {

  implicit val timeout = Timeout(5.seconds)

  override def createOrder(id: String): ServiceCall[OrderRequest, OrderResponse] = ServiceCall { order =>
    entityRef(id)
      .ask(reply =>
        CreateOrder(order.serverId, order.tableId, order.items.map(item => Item(item.name, item.specialInstructions)), reply))
      .map(confirmation => confirmationToResult(id, confirmation))
  }

  def confirmationToResult(id: String, confirmation: Confirmation): OrderResponse = {
    println(s"I'm confirming!! $confirmation")
    confirmation match {
      case Accepted(cartSummary) => OrderResponse(id, cartSummary.serverId, cartSummary.tableId, cartSummary.items.map(i => ItemRequest(i.name, i.specialInstructions)))
      case Rejected(reason) => throw BadRequest(reason)
    }
  }

  /**
    * Looks up the entity for the given ID.
    */
  private def entityRef(id: String): EntityRef[PondCommand] =
    clusterSharding.entityRefFor(PondState.typeKey, id)

  override def getOrder(id: String) = ServiceCall { request =>
    // Look up the sharded entity (aka the aggregate instance) for the given ID.
    entityRef(id)
      .ask(
        replyTo => GetOrder(replyTo)
      ).map(cartSummary => convertShoppingCart(id, cartSummary))

  }

  private def convertShoppingCart(id: String, cartSummary: Summary) = {
    OrderResponse(
      id,
      cartSummary.serverId,
      cartSummary.tableId,
      cartSummary.items.map { item => ItemRequest(item.name, item.specialInstructions) }
    )
  }

  //how this works???
  override def ordersTopic(): Topic[ExternalPondEvent] =
    TopicProducer.singleStreamWithOffset { fromOffset =>
      persistentEntityRegistry
        .eventStream(PondEvent.Tag, fromOffset)
        .map(ev => (convertEvent(ev), ev.offset))
    }

  private def convertEvent(helloEvent: EventStreamElement[PondEvent]): ExternalPondEvent = {
    helloEvent.event match {
      case OrderCreated(serverId, tableId, items) => ExternalPondEvent(helloEvent.entityId, serverId, tableId, items.map( x => ItemRequest(x.name,x.specialInstructions)))
    }
  }

}
