# Pond

This project has been generated by the lagom/lagom-scala.g8 template. 

For instructions on running and testing the project, see https://www.lagomframework.com/get-started-scala.html.



/ write model
create order (server name, table number)
add item to order (order id, item name, special instruction)
retrieve order (order id)

/ read model 
give me all orders

https://www.lagomframework.com/documentation/1.6.x/scala/UsingAkkaPersistenceTyped.html

https://www.lagomframework.com/documentation/1.6.x/scala/PersistentEntity.html


testable it with: 

to create order 

    curl -H "Content-Type: application/json" -X POST -d '{"serverId":"s1", "tableId":"t1", "items":[{"name":"name1","specialInstructions":"isnt1","quantity":3}]}' http://localhost:9000/api/order/12345

to add item
    
    curl -H "Content-Type: application/json" -X POST -d '{"serverId":"s1", "tableId":"t1", "items":[{"name":"name4","specialInstructions":"isnt4","quantity":4}]}' http://localhost:9000/api/order/12345/add
    
to get order

    curl  -X GET http://localhost:9000/api/order/12345
    
    
To read from the Journal, a `JournalReader` App is provided. The one in `pond-impl` works, but the one in `pound-journal-reader` doesn't.
    
       