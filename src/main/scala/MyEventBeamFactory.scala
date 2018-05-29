import com.metamx.common.Granularity
import com.metamx.tranquility.beam.{Beam, ClusteredBeamTuning}
import com.metamx.tranquility.druid._
import com.metamx.tranquility.spark.BeamFactory
//import com.metamx.tranquility.typeclass.Timestamper

//import io.druid.data.input.impl.TimestampSpec
import io.druid.granularity.QueryGranularities
import io.druid.query.aggregation.LongSumAggregatorFactory

import org.apache.curator.framework.CuratorFrameworkFactory
import org.apache.curator.retry.BoundedExponentialBackoffRetry

import org.joda.time.{DateTime, Period}
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

// event schema: YYYY/MM/DD HH:mm:ss, eventType, eventClient, eventHost, eventMetric, eventValue
case class MyEvent(timestamp: String,
                   eventType: String,
                   eventClient: String,
                   eventHost: String,
                   eventMetric: String,
                   eventValue: Long) {
}

object MyEvent {
  val SEPARATOR = ","
  val dtf: DateTimeFormatter = DateTimeFormat.forPattern("yyyy/MM/dd HH:mm:ss")

  val timestampCol = "timestamp"
  val dimensions = Seq("eventType", "eventClient", "eventHost", "eventMetric")
  val metrics = Seq("eventValue")

// not needed here as we already have a timestamp field
/*
  implicit val myEventTimestamper = new Timestamper[MyEvent] {
    def timestamp(e: MyEvent) = dtf.parseDateTime(e.timestamp)
  }
*/

  def fromCSV(s: String): MyEvent = {
    val t = s.split(SEPARATOR)

    MyEvent(
      // we override the timestamp of the event to avoid conflicts (with the windowing time)
      // if re-processing "old" messages from the Kafka topic while testing.
      // This could be achieved also via the windowPeriod param in the beam factory...
      timestamp = DateTime.now.toString, //t(0),
      eventType = t(1),
      eventClient = t(2),
      eventHost = t(3),
      eventMetric = t(4),
      eventValue = t(5).toLong
    )
  }
}

class MyEventBeamFactory extends BeamFactory[MyEvent] {

  // singleton here allows a shared connection per JVM
  lazy val makeBeam: Beam[MyEvent] = {
    // ZooKeeper (via the Curator framework) is used for coordination by Tranquility, retry should be tuned
    val curator = CuratorFrameworkFactory.newClient(
      "localhost:2181",
      new BoundedExponentialBackoffRetry(100, 3000, 5)
    )
    curator.start

    val indexService = DruidEnvironment("druid/overlord")
    val discoveryPath = "/druid/discovery"
    val dataSource = "tbd-origin"
    val aggregators = MyEvent.metrics.map(m => new LongSumAggregatorFactory(m, m))
    // this would use myEventTimestamper...
    val timestampFunc = (e: MyEvent) => new DateTime(e.timestamp)

    // performance-related parameters to be tuned (roll-up/segment granularity, and window period)
    // partitions and replicants would require tuning as well
    val druidRollup = DruidRollup(
      SpecificDruidDimensions(MyEvent.dimensions),
      aggregators, // defined based on query workload and use-case
      QueryGranularities.MINUTE // min query granularity for OLAP queries
    )
    val tuning = ClusteredBeamTuning(
      segmentGranularity = Granularity.FIVE_MINUTE, // time to flush a segment to disk
      windowPeriod = new Period("PT1M"),
      partitions = 1,
      replicants = 1
    )

    DruidBeams
      .builder(timestampFunc)
      .curator(curator)
      .discoveryPath(discoveryPath)
      .location(DruidLocation(indexService, dataSource))
      .rollup(druidRollup)
      .tuning(tuning)
      //.timestampSpec(new TimestampSpec(MyEvent.timestampCol, "auto", null)) /* not needed here */
      .buildBeam
  }
}