# how to do arbitrary transaction scoping with Zio and Doobie

To see the presentation [go to](https://the-source.it/zio-doobie-transactions/presentation/index.html)

Or run the revealjs server:     
```cd presentation && npm install```    
and then
```npm start```  

Then go to [the presentation on localhost:8000](http://localhost:8000)



The code
- [a simple sql program. ZIO code and transactions live in separate worlds ](code/app/src/test/scala/demo/DoobieZio0.scala)
- [transactional scoping of our zio code that we see in the types ](code/app/src/test/scala/demo/DoobieZio1.scala)
- [transactional scoping of our zio code that we don't see in the types ](code/app/src/test/scala/demo/DoobieZio2.scala)
- [transactional scoping of our zio code with scala 3 context functions ](code/app/src/test/scala/demo/DoobieZio3.scala)

To run the code first start the db with  
```docker compose up -d --wait```
