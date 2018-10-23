package com.ccfs.util

import com.ccfs.daos.UserDAO._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Utils {

  import com.ccfs.Main.dbConfig.profile.api._

  def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  def run(db: Database): Unit = {

    def loop(page: Int = 0): Unit = {
      getUserPage(db, page).onComplete {
        case Success(users) =>
          if (users.nonEmpty) {
            // todo Pull the data for these users and write to file
            println(s"processing ${users.length} users")
            loop(page + 1)
          } else synchronized {
            notify()
          }

        case Failure(e) =>
          // todo
          println(s"Oooops: ${e.getClass.getName}")
      }
    }

    loop()

    synchronized {
      wait()
    }
  }
}
