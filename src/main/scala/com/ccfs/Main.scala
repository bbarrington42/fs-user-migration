package com.ccfs

import java.io.File

import Extractor._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Main {

  // How many users to query at a time
  val PAGE_SIZE = 5000

  // Directory where data files are to be written
  val dirName = "data"

  // Base name of files
  val basename = "userdata"

  val CSV = "csv"

  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db-config")

  import dbConfig.profile.api._


  val dir = new File(dirName)
  dir.mkdir()

  def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  // Do it
  def main(args: Array[String]): Unit = using(dbConfig.db)(db => run(db, dir))

}
