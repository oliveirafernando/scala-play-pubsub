name := """scala-play-pubsub"""
organization := "com.oliveirafernando"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.6"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test

// https://cloud.google.com/pubsub/docs/reference/libraries
libraryDependencies += "com.google.cloud" % "google-cloud-pubsub" % "1.114.7"

val AkkaVersion = "2.6.14"
val AkkaHttpVersion = "10.1.14"
libraryDependencies ++= Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % "3.0.3",
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
)
