package com.lightbend.pond.api

import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import play.api.libs.json.{Format, Json}

object PondService {
  val TOPIC_NAME = "restaurant-orders"
}

/**
  * The Pond service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the PondService.
  */
trait PondService extends Service {


  override final def descriptor: Descriptor = {
    import Service._
    // @formatter:off
    named("pond")
      .withCalls(
        restCall(Method.GET, "/api/order/:id", getOrder _),
        restCall(Method.POST, "/api/order/:id", createOrder _),
          restCall(Method.POST, "/api/order/:id/add", addItem _)
      )
      .withTopics(
        topic(PondService.TOPIC_NAME, ordersTopic _)
          // Kafka partitions messages, messages within the same partition will
          // be delivered in order, to ensure that all messages for the same user
          // go to the same partition (and hence are delivered in order with respect
          // to that user), we configure a partition key strategy that extracts the
          // name as the partition key.
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[OrderResponse](_.id)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }


  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
    * "Hi"}' http://localhost:9000/api/hello/Alice
    */
  def createOrder(id: String): ServiceCall[OrderRequest, OrderResponse]


  def addItem(id: String): ServiceCall[ItemRequest, OrderResponse]
  /**
    * Example: curl -H "Content-Type: application/json" -X POST -d '{"message":
    * "Hi"}' http://localhost:9000/api/hello/Alice
    */
  def getOrder(id: String): ServiceCall[String, OrderResponse]

  /**
    * This gets published to Kafka.
    */
  def ordersTopic(): Topic[OrderResponse]


}


case class OrderRequest(tableId: String, serverId: String, items: Seq[ItemRequest])

object OrderRequest {

  implicit val format: Format[OrderRequest] = Json.format[OrderRequest]

}

case class ItemRequest(name: String, specialInstructions: String, quantity: Int)

object ItemRequest {
  implicit val format: Format[ItemRequest] = Json.format[ItemRequest]

}

case class OrderResponse(id: String, tableId: String, serverId: String, items: Seq[ItemRequest])

object OrderResponse {
  implicit val format: Format[OrderResponse] = Json.format[OrderResponse]

}



