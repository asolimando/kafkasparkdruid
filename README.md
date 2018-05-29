# kafkasparkdruid
Example of reading from a Kafka topic via Spark Streaming and writing into Druid via Tranquility library

Implementation Details and Setup

The implementation has been tested on Linux box (Ubuntu 16.04.1 64bit) with a single-machine setup, and the following SW: 
IntelliJ 2018.1
Druid 0.12.0
Kafka 0.10.1.0 (Spotify Docker: https://github.com/spotify/docker-kafka)
Spark 2.3


Sample CSV Data

The input data format (other than an idea of its schema) has not been provided, we have assumed CSV (“,” as separator), generated via a bash command and sent to Kafka (“tdb-origin” topic) via “kafka-console-producer.sh” (from Kafka v1.1.0, but that’s irrelevant). Run “event_gen.sh” script to generate sample data (Kafka must be running). Note that we do not set any key for our messages, as we adopt a toy setup with a single partitions, but this is detrimental for real-systems.
Kafka Docker

Assuming a running docker service, pull the sought docker image (latest now ships Kafka v0.10.1.0, but might change):
sudo docker pull spotify/kafka

Then run the Kafka docker taking care of exposing Kafka and Zookeper ports on the local machine (Zookeeper is needed by Druid, Kafka must be reachable by the Spark Job):

sudo docker run --name kafka -p 2181:2181 -p 9092:9092 --env ADVERTISED_HOST=localhost --env ADVERTISED_PORT=9092 spotify/kafka > kafka.log&

Create the “tbd-origin” topic (partitions and replications should be increased):

sudo docker exec kafka $kafkapath/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --if-not-exists --partitions 1 --topic tbd-origin


Druid

None of the available Druid Docker images provides the needed setup, it is easier to download it and run it on the local machine via the following commands (in this order, from the druid installation directory, and waiting few seconds in-between them, ZooKeeper must already be running and must be reachable at port 2181):

java `cat conf-quickstart/druid/coordinator/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/coordinator:lib/*" io.druid.cli.Main server coordinator > log/coordinator.log
java `cat conf-quickstart/druid/historical/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstt/druid/historical:lib/*" io.druid.cli.Main server historical > log/historical.log
java `cat conf-quickstart/druid/broker/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/broker:lib/*" io.druid.cli.Main server broker > log/broker.log
java `cat conf-quickstart/druid/overlord/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstdruid/overlord:lib/*" io.druid.cli.Main server overlord > log/overlord.log
java `cat conf-quickstart/druid/middleManager/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/middleManager:lib/*" io.druid.cli.Main server middleManager > log/middlemanager.log

On a laptop equipped with 8GB of RAM, a smooth run required to reduce the JVM memory for Druid daemons (in the respective jvm.config files) as follows:

Broker: 128m
Coordinator: 128m
Historical: 128m
MiddleManager: 64m
Overlord: 1g

The progress of the ingestion tasks can be monitored here:
http://localhost:8081/#/indexing-service

Once the segments are flushed to disk, they are available as historical data (note that queries will be answered w.r.t. both historical data and streaming data, even if still under indexing), statistics are provided here:
http://localhost:8081/#/datasources


Sample Query

A sample query is available as “query.json”, and can be runned at ingestion time against Druid (to verify changes in query result if newly ingested data is part of the result set) with the following command:

watch curl -X POST 'localhost:8082/druid/v2/?pretty' -H 'Content-Type:application/json' -d @query.json

The time range must be updated before running the query (an end for the query range in the future stands for unbounded range).


Spark Code

In this section we review the main elements of the proposed implementation, what has not been addressed and which are the parameters/aspects that would be addressed and tuned for a real deployment.
KafkaSparkDruid.scala

What is provided in this file can be summarised as follows:

Creation of a SparkContext
Creation of a (Kafka) stream
Deserialisation of the (binary) value of our messages to a CSV string, conversion to a custom event type, MyEvent (using the type-safe processing offered by StructuredStreaming)
Mini-batch is consumed at regular intervals and written to Druid through Tranquility library invoked by means of a custom Spark ForeachWriter


Exactly-once Semantics

Spark relies on the (fault-tolerant) offset handling provided by Kafka, and as we do not perform stateful processing (that is, no windowing and no aggregation) checkpointing is not needed (SparkJob can fail and be restarted, it will simply consume from the last seen offset). 

If a failure happens at the level of a SparkJob (not single executor) some messages might be lost. Suppose that a message is consumed, the (new) offset is auto-commited. Now, if the failure happens before sending the message to Druid (or if the message gets lost over the network), the event is lost. In order to really guarantee exactly-once semantics, offset should be programmatically committed by Spark, after checking the successful state of the message (associated to that event) sent to Druid. This extra processing might harm ingestion performance.

MyEventBeamFactory.scala
This file is composed by two main elements:
MyEvent: the case class representing an event, with metadata to easily identify dimensions and metrics
MyEventBeamFactory: the (singleton) connection to Druid (and associated ingestion parameters, discussed later) offered via Tranquility


(Known) Unaddressed Aspects

Error handling and hardening, for instance, DruidWriter sends events to Druid, but it does not verify the status nor handle failures (see considerations on exactly-once semantics guarantee)
All the code assumes a single-machine setup, it should be adapted to use a cluster
Parameter tuning: there are many parameters that would need a proper benchmarking and tuning w.r.t. the use-case, they are commented in the code
Code quality: parameters should be properly externalised in a configuration file and/or parameters map/Java properties, the same goes for externalisation of strings
UTC default timezone could not be the right choice, code should be made explicitly timezone-aware (it should be set in the configuration, and appropriate overloaded methods should be used)
Group-id for Kafka not handled here (only a single consumer, a SparkJob with a single executor)


Parameters and Tuning

Several parameters should be tuned and adapted to improve performance and reliability, based on the real use-case and the running environment, such as:

Custom partitioner for Tranquility: to partition based on other dimensions and not only on timestamp, the right choice should be driven by analysis of the query workload (e.g., common query 2 would benefit a partitioning over eventType dimension) and a benchmarking of the different solutions (different queries might require incompatible optimisations)
Spark number of executors and spark cluster parameters (executors/driver memory, number of cores etc.)
Mini-batch triggering time should be adjusted to meet desired ingestion throughput and latency (as a reminder, real-time processing will be available in Spark v2.4)

Tranquility parameters:
Zookeeper connection retry policy and parameters
Partitions/replicants in Druid for our segments (affects memory footprint of our dataset, but also query processing and fault-tolerance)
Ingestion window (Druid realtime ingestion only accepts events within a configurable windowPeriod of the current time, to ignore events that are too old)
Segment granularity (ingestion rate, message size and segment granularity influences the number and size of segments, negatively affecting query performance if segments are too many and small, or too few and massive)
Rollup: query granularity, aggregators, rollup dimensions, they all affect the supported queries and their performance, they should be set according to the query workload


Library Dependencies

Note that guava had to be forced (see build.sbt) to version 16.0.1 (Spark ships an unshaded Guava 11 version that misses some classes used by Tranquility, used in our code to implement a Druid writer for Spark).
