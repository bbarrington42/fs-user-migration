import sbtassembly.AssemblyPlugin.defaultShellScript

assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript))

assemblyJarName in assembly := "fs-user-migration"


name := "fs-user-migration"

version := "0.1"

scalaVersion := "2.12.7"

scalacOptions ++= Seq(
  "-feature",
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-unchecked",
  "-deprecation",
  "-Xfuture",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen"
)


libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.3",
  "mysql" % "mysql-connector-java" % "5.1.38",
  "ch.qos.logback" % "logback-core" % "1.2.3",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.play" %% "play-json" % "2.6.10",
  "com.opencsv" % "opencsv" % "4.3.2",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.3",
  "org.scalaz" %% "scalaz-core" % "7.2.26"
)
