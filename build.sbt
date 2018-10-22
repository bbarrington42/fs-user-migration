name := "fs-user-migration"

version := "0.1"

scalaVersion := "2.12.7"



libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "mysql" % "mysql-connector-java" % "5.1.38",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.play" %% "play-json" % "2.6.10"
)
