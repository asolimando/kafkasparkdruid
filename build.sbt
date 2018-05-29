name := "kafkasparkdruid"

version := "1.0"

scalaVersion := "2.11.8"

val SPARK_VERSION = "2.3.0"

libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % SPARK_VERSION,
  "org.apache.spark" %% "spark-sql" % SPARK_VERSION,
  "org.apache.spark" %% "spark-sql-kafka-0-10" % SPARK_VERSION,

  "io.druid" %% "tranquility-spark" % "0.8.2",

  "joda-time" % "joda-time" % "2.9.4",
  "org.joda" % "joda-convert" % "1.2"
)

// override guava-11 shipped with spark (not shaded) as needed by tranquility
dependencyOverrides ++= Seq("com.google.guava" % "guava" % "16.0.1")
