import com.metamx.tranquility.spark.BeamFactory

import org.apache.spark.sql.{ForeachWriter, SparkSession}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.ProcessingTime

object KafkaSparkDruid  {

  def main(args: Array[String]) {

    val spark = SparkSession
      .builder
      .master("local[1]") // number of executors and spark cluster params to be tuned
      .appName("KafkaSparkDruid")
      .getOrCreate

    val df = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "localhost:9092")
      .option("subscribe", "tbd-origin")
      //.option("startingOffsets", "earliest") // decomment for full event replay
      .load

    df.printSchema

    import spark.implicits._

    // deserialize csv string, then build an event from it
    val df_events = df.select(col("value").cast("string") as "value")
                      .map(r => MyEvent.fromCSV(r.getString(0)))

    df_events.printSchema

    case class DruidWriter[T](beam: BeamFactory[T]) extends ForeachWriter[T] {
      override def open(partitionId: Long, version: Long) = true
      override def process(value: T) = {
        val futures = beam.makeBeam.sendAll(Seq(value))
        /* error handling of features here */
      }
      override def close(errorOrNull: Throwable) = { /* error handling here */ }
    }

    val ds = df_events.writeStream
      .trigger(ProcessingTime(1000)) // mini-batch triggered every second, should to be tuned
      .foreach(DruidWriter(new MyEventBeamFactory))
      .start

    ds.awaitTermination
  }
}