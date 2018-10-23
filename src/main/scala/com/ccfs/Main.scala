package com.ccfs

import com.ccfs.util.Utils._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Main {
  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db-tunnel")

  def main(args: Array[String]): Unit = {

    using(dbConfig.db)(db => run(db))
  }

}
