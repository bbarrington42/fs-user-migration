package com.ccfs.util

import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}

import com.ccfs.daos.UserDAO._
import com.ccfs.model.UserModel.{MixItem, User, UserMix}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}

object Utils {

  import com.ccfs.Main.dbConfig.profile.api._

  def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  class Synchronize(lock: Lock, cond: Condition) {
    def pause() = {
      lock.lock()
      try {
        cond.await()
      } finally {
        lock.unlock()
      }
    }

    def continue() = {
      lock.lock()
      try {
        cond.signal()
      } finally {
        lock.unlock()
      }
    }
  }

  object Synchronize {
    def apply() = {
      val lock = new ReentrantLock()
      new Synchronize(lock, lock.newCondition())
    }
  }

  /*
  {
  "favorites": [
    1596915,
    1574026,
    1574025
  ],
  "mixes": [
    {
      "name": "Mix1",
      "mixItems": [
        {
          "bevID": 1565599,
          "ratio": 25
        },
        {
          "bevID": 1475614,
          "ratio": 41
        },
        {
          "bevID": 1482850,
          "ratio": 34
        }
      ]
    }
  ]
}

   */

  private def writeToFile(data: Seq[(String, Seq[(UserMix, Seq[MixItem])], Seq[Int])]): Unit = {
    // todo
    println(s"DATA: $data")
  }

  private def process(db: Database, users: Seq[User]): Unit = {
    val synch = Synchronize()

    val f = Future.sequence(users.map(user =>
      getUserPrefs(db, user.id).map { case (mixes, favs) => (user.jrid, mixes, favs) }))
    f.onComplete {
      case Success(data) =>
        writeToFile(data)
        synch.continue()

      case Failure(e) =>
        println(s"Oooops: ${e.getClass.getName}")
        synch.continue()
    }
    synch.pause()
  }

  def run(db: Database): Unit = {
    val synch = Synchronize()

    def loop(page: Int = 0): Unit = {
      getUserPage(db, page).onComplete {
        case Success(users) =>
          if (users.nonEmpty) {
            // Pull the data for these users and write to file
            println(s"processing ${users.length} users")
            process(db, users)
            loop(page + 1)
          } else synch.continue()

        case Failure(e) =>
          // todo
          println(s"Oooops: ${e.getClass.getName}")
          synch.continue()
      }
    }

    loop()

    synch.pause()
  }
}
