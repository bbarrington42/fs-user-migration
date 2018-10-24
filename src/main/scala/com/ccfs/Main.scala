package com.ccfs

import java.io.File

import Extractor._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Main {

  // How many users to query at a time
  val PAGE_SIZE = 2000

  // Directory where data files are to be written
  val dirName = "data"

  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db-config")

  val dir = new File(dirName)
  dir.mkdir()

  // Do it
  def main(args: Array[String]): Unit = using(dbConfig.db)(db => run(db, dir))

}
