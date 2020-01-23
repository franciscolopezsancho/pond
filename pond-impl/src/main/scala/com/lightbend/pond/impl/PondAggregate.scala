package com.lightbend.pond.impl

import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl._
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect.reply
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, AkkaTaggerAdapter}
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import com.lightbend.pond.impl.PondState.{Item, PondCommand}
import play.api.libs.json.{Format, Json}

import scala.collection.immutable.Seq

object PondBehavior {

  /**
    * Given a sharding [[EntityContext]] this function produces an Akka [[Behavior]] for the aggregate.
    */
  def create(entityContext: EntityContext[PondCommand]): Behavior[PondCommand] = {
    val persistenceId: PersistenceId = PersistenceId(entityContext.entityTypeKey.name, entityContext.entityId)

    create(persistenceId)
      .withTagger(
        // Using Akka Persistence Typed in Lagom requires tagging your events
        // in Lagom-compatible way so Lagom ReadSideProcessors and TopicProducers
        // can locate and follow the event streams.
        AkkaTaggerAdapter.fromLagom(entityContext, PondEvent.Tag)
      )

  }

  /*
   * This method is extracted to write unit tests that are completely independendant to Akka Cluster.
   */
  private[impl] def create(persistenceId: PersistenceId) = EventSourcedBehavior
    .withEnforcedReplies[PondCommand, PondEvent, PondState](
      persistenceId = persistenceId,
      emptyState = PondState.initial,
      commandHandler = (cart, cmd) => cart.applyCommand(cmd),
      eventHandler = (cart, evt) => cart.applyEvent(evt)
    )
}

/**
  * The current state of the Aggregate.
  */
case class PondState(tableId: String, serverId: String, items: Seq[Item]) {


  import PondState._


  def applyCommand(cmd: PondCommand): ReplyEffect[PondEvent, PondState] =
    cmd match {
      case x: CreateOrder => onCreateOrder(Order(x.tableId, x.serverId, Seq.empty[Item]), x.replyTo)
      case x: GetOrder => onGetOrder(x.replyTo)
    }

  private def onCreateOrder(cmd: Order, replyTo: ActorRef[Confirmation]): ReplyEffect[PondEvent, PondState] = {
    println("persisting onCreateOrder")
    Effect
      .persist(toState(cmd))
      .thenReply(replyTo)(updatedCart => Accepted(toSummary(updatedCart)))
  }

  private def toSummary(state: PondState): Summary =
    Summary(state.tableId, state.serverId, state.items)

  private def toState(order: Order): OrderCreated =
    OrderCreated(serverId = order.serverId, tableId = order.tableId, items = order.items ++ items)

  private def onGetOrder(replyTo: ActorRef[Summary]): ReplyEffect[PondEvent, PondState] = {
    reply(replyTo)(toSummary(this))
  }

  def applyEvent(evt: PondEvent): PondState =
    evt match {
      case o: OrderCreated => updateMessage(o)
    }

  def updateMessage(order: OrderCreated): PondState =
    copy(items = items ++ order.items)


}

trait PondCommandSerializable

object PondState {
  /**
    * The [[EventSourcedBehavior]] instances (aka Aggregates) run on sharded actors inside the Akka Cluster.
    * When sharding actors and distributing them across the cluster, each aggregate is
    * namespaced under a typekey that specifies a name and also the type of the commands
    * that sharded actor can receive.
    */
  val typeKey = EntityTypeKey[PondCommand]("PondAggregate")

  /**
    * The initial state. This is used if there is no snapshotted state to be found.
    */
  def initial: PondState = PondState("initial", "initial", Seq.empty)

  sealed trait Confirmation

  /**
    * This interface defines all the commands that the PondAggregate supports.
    */
  sealed trait PondCommand
    extends PondCommandSerializable

  case class Order(tableId: String, serverId: String, items: Seq[Item])

  case class Item(name: String, specialInstructions: String)

  final case class Accepted(summary: Summary) extends Confirmation

  implicit val formatItem: Format[Item] = Json.format[Item]
  implicit val confirmationItem: Format[Confirmation] = Json.format[Confirmation]
  implicit val rejectedFormat: Format[Rejected] = Json.format[Rejected]
  implicit val summaryItem: Format[Summary] = Json.format[Summary]
  implicit val acceptedItem: Format[Accepted] = Json.format[Accepted]
  implicit val formatOrder: Format[Order] = Json.format[Order]

  final case class Rejected(reason: String) extends Confirmation

  final case class Summary(serverId: String, tableId: String, items: Seq[Item])

  final case class CreateOrder(serverId: String, tableId: String, items: Seq[Item], replyTo: ActorRef[Confirmation]) extends PondCommand

  final case class GetOrder(replyTo: ActorRef[Summary]) extends PondCommand

  final case class AddItem(itemId: String, quantity: String, replyto: ActorRef[Order]) extends PondCommand

  /**
    * Format for the hello state.
    *
    * Persisted entities get snapshotted every configured number of events. This
    * means the state gets stored to the database, so that when the aggregate gets
    * loaded, you don't need to replay all the events, just the ones since the
    * snapshot. Hence, a JSON format needs to be declared so that it can be
    * serialized and deserialized when storing to and from the database.
    */
  implicit val format: Format[PondState] = Json.format
}

/**
  * This interface defines all the events that the PondAggregate supports.
  */
sealed trait PondEvent extends AggregateEvent[PondEvent] {
  def aggregateTag: AggregateEventTag[PondEvent] = PondEvent.Tag
}

object PondEvent {
  val Tag: AggregateEventTag[PondEvent] = AggregateEventTag[PondEvent]
}


case class OrderCreated(serverId: String, tableId: String, items: Seq[Item])
  extends PondEvent

/**
  * This is a marker trait for commands.
  * We will serialize them using Akka's Jackson support that is able to deal with the replyTo field.
  * (see application.conf)
  */
//TODO why this trait?


/**
  * A command to switch the greeting message.
  *
  * It has a reply type of [[Confirmation]], which is sent back to the caller
  * when all the events emitted by this command are successfully persisted.
  */


/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object PondSerializerRegistry extends JsonSerializerRegistry {

  import PondState._

  override def serializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    JsonSerializer[PondState],
    // the replies use play-json as well
    JsonSerializer[Summary],
    JsonSerializer[Confirmation],
    JsonSerializer[Accepted],
    JsonSerializer[Rejected],
    // the replies use play-json as well
  )
}
