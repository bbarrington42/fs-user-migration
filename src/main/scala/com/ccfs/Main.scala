package com.ccfs

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object Main {
  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("tunnel")

  import dbConfig.profile.api._

  private def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  using(dbConfig.db)(db => {
    // todo

  })


}
