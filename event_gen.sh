#!/bin/bash

declare -a metrics=( "COUNT" "MAX" "MIN" )

end=$((SECONDS+1))
kafkadir="kafka_2.11-1.1.0"
topic=tbd-origin

while [ $SECONDS -lt $end ]; do
   echo $(date -dyesterday "+%Y/%m/%d %H:%M:%S"),TYPE$((RANDOM % 10)),C$RANDOM,host$RANDOM,${metrics[$((RANDOM % 3))]},$RANDOM
done | ~/apps/$kafkadir/bin/kafka-console-producer.sh --broker-list localhost:9092 --topic $topic

sleep 10
curl -X POST 'localhost:8082/druid/v2/?pretty' -H 'Content-Type:application/json' -d @query.json

#watch curl -X POST 'localhost:8082/druid/v2/?pretty' -H 'Content-Type:application/json' -d @query.json
