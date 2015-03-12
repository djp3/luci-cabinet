This is a java interface to the [tokyo cabinet](http://1978th.net/tokyocabinet/) key-value database system.  It is fast, simple to use and flexible.  **If** you can set up tokyo-cabinet and tokyo-cabinet-java then it is also simple to set up.

Goals:

  * Support concurrent access to one key-value database.
  * Support network based access to one key-value database. (like tokyo-tyrant, but in java)
  * Support sharding across multiple key-value databases.

Use Cases:
  1. "I want to use Tokyo Cabinet, and my code is in Java"
    * a. "I want to use a Tokyo Cabinet Hash DB" ([Use Case 1a](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase1a.java))
    * b. "I want to use a Tokyo Cabinet B+Tree DB" ([Use Case 1b](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase1b.java))
  1. "I have several threads that need to access the same LUCICabinetMap database concurrently from Java" ([Use Case 2](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase2.java))
  1. "I want to access a LUCICabinetMap database across the network using Java" ([Use Case 3](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase3.java))
  1. "I want to access a LUCICabinetMap database across the network from several locations and from several threads using Java" ([Use Case 4](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase4.java))
  1. "I want to shard by database calls to several LUCICabinetMap databases" ([Use Case 5](http://code.google.com/p/luci-cabinet/source/browse/trunk/src/usecase/UseCase5.java))

> ![http://luci-cabinet.googlecode.com/svn/trunk/docs/UseCases.png](http://luci-cabinet.googlecode.com/svn/trunk/docs/UseCases.png)

Performance:
  * To test performance we implemented several different types of luci-cabinet configurations.  The code is in the repository. We used the following specs:
    * tokyocabinet 1.4.42
    * tokyo-java 1.22
    * Java 1.6
    * gcc 4.0.1 (Apple build 5493)
    * Ran on
      * MacBook Pro OSX 10.5.8
      * 2.6 GHz Intel Core 2 Duo with 4GB of 667 Mhz DDR2 SDRAM
    * The test consisted of
      * 1,000,000 writes of Integer keys and Integer values (0-999,999) into an empty database
      * 1,000,000 reads from the previous database
      * 10 shards for the sharded databases
      * all shards and remote databases were hosted on the same machine

> ![http://luci-cabinet.googlecode.com/svn/trunk/docs/Performance.png](http://luci-cabinet.googlecode.com/svn/trunk/docs/Performance.png)

Notes on Performance:
  * Remote tests are signficantly slower due to socket communication
  * Most of the time is spent serializing and deserializing the data

