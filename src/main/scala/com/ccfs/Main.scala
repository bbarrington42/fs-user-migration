package com.ccfs

import com.ccfs.daos.UserDAO
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Main {
  val dbConfig = DatabaseConfig.forConfig[JdbcProfile]("db-tunnel")

  import dbConfig.profile.api._

  private def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  def main(args: Array[String]): Unit = {

    using(dbConfig.db)(db => {
      val users = UserDAO.getUsers(db)

      val res = Await.result(users, Duration.Inf)

      println(res.length)

    })
  }


}
