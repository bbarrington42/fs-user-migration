package com.ccfs

import java.io.File

import Extractor._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Main {
  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db-config")

  // Directory where data files are to be written
  val dir = new File("data")

  // Do it
  def main(args: Array[String]): Unit = using(dbConfig.db)(db => run(db, dir))

}
