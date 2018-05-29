#!/bin/bash

kafkapath=/opt/kafka_2.11-0.10.1.0

#### pull docker containers ####
# https://github.com/spotify/docker-kafka
sudo docker pull spotify/kafka

#### start docker containers ####
sudo docker run --name kafka -p 2181:2181 -p 9092:9092 --env ADVERTISED_HOST=localhost --env ADVERTISED_PORT=9092 spotify/kafka > kafka.log&
sleep 10

declare -a topics=( "tbd-origin" )

for i in "${topics[@]}"
do
  sudo docker exec kafka $kafkapath/bin/kafka-topics.sh --zookeeper localhost:2181 --delete --topic $i

  sudo docker exec kafka $kafkapath/bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --if-not-exists --partitions 1 --topic $i
done

# to be runned inside the druid installation directory
#java `cat conf-quickstart/druid/coordinator/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/coordinator:lib/*" io.druid.cli.Main server coordinator > log/coordinator.log

#java `cat conf-quickstart/druid/historical/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstt/druid/historical:lib/*" io.druid.cli.Main server historical > log/historical.log

#java `cat conf-quickstart/druid/broker/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/broker:lib/*" io.druid.cli.Main server broker > log/broker.log

#java `cat conf-quickstart/druid/overlord/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstdruid/overlord:lib/*" io.druid.cli.Main server overlord > log/overlord.log

#java `cat conf-quickstart/druid/middleManager/jvm.config | xargs` -cp "conf-quickstart/druid/_common:conf-quickstart/druid/middleManager:lib/*" io.druid.cli.Main server middleManager > log/middlemanager.log




