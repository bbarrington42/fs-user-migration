package com.ccfs.util

import java.io.{File, FileWriter}
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}

import au.com.bytecode.opencsv.CSVWriter
import com.ccfs.daos.UserDAO._
import com.ccfs.model.UserModel.{MixItem, User, UserMix}
import play.api.libs.json.{JsArray, JsNumber, JsObject, Json}

import scala.collection.JavaConverters._
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
  "qrid": "AE628FF6-D892-5A27-CE0C-60B5FD6BCF81#0238E7CF",
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
    },
    {
      "name": "Mix2",
      "mixItems": [
        {
          "bevID": 1866601,
          "ratio": 37
        },
        {
          "bevID": 1866599,
          "ratio": 38
        },
        {
          "bevID": 1475650,
          "ratio": 25
        }
      ]
    },
    {
      "name": "Mix3",
      "mixItems": [
        {
          "bevID": 1522597,
          "ratio": 34
        },
        {
          "bevID": 1475650,
          "ratio": 33
        },
        {
          "bevID": 1475673,
          "ratio": 33
        }
      ]
    }
  ]
}

   */

  private def mixItemToJson(mixItem: MixItem): JsObject = Json.obj(
    "bevID" -> mixItem.beverageId,
    "ratio" -> mixItem.ratio
  )

  private def mixItemsToJson(mixItems: Seq[MixItem]): JsArray =
    Json.arr(mixItems.map(mixItemToJson))

  private def mixToJson(mix: UserMix, mixItems: Seq[MixItem]): JsObject = Json.obj(
    "name" -> mix.name,
    "mixItems" -> mixItemsToJson(mixItems)
  )

  private def favsToJson(favs: Seq[Int]): JsArray =
    Json.arr(favs.map(JsNumber(_)))

  private def mixesToJson(mixes: Seq[(UserMix, Seq[MixItem])]): JsArray =
    Json.arr(mixes.map { case (mix, mixItems) => mixToJson(mix, mixItems) })

  private def mixesAndFavsToJson(mixes: Seq[(UserMix, Seq[MixItem])], favs: Seq[Int]): JsObject = Json.obj(
    "favorites" -> favsToJson(favs),
    "mixes" -> mixesToJson(mixes)
  )

  private def writeToFile(data: Seq[(Option[String], Seq[(UserMix, Seq[MixItem])], Seq[Int])], page: Int, dir: File): Unit = {
    // Map to a List of string arrays. Each component of the Array is an element to be separated with commas.
    val lines = data.toList.map { case (jrid, mixes, favs) =>
      jrid.fold(Array.empty[String])(j => Array(j, Json.stringify(mixesAndFavsToJson(mixes, favs))))
    }.filterNot(_.isEmpty)

    // Create file to write to
    val file = new File(dir, f"$page%03d.csv")
    val csv = new CSVWriter(new FileWriter(file))
    try {
      csv.writeAll(lines.asJava)
    } finally {
      csv.close()
    }
  }

  private def process(db: Database, users: Seq[User], page: Int, dir: File): Unit = {
    val synch = Synchronize()

    Future.sequence(users.map(user =>
      getUserPrefs(db, user.id).map { case (mixes, favs) => (user.jrid, mixes, favs) })).onComplete {
      case Success(data) =>
        writeToFile(data, page, dir)
        synch.continue()

      case Failure(e) =>
        println(s"Oooops: ${e.getClass.getName} - ${e.getMessage}")
        synch.continue()
    }
    synch.pause()
  }

  def run(db: Database, dir: File): Unit = {
    val synch = Synchronize()

    def loop(page: Int = 0): Unit = {
      getUsers(db, page).onComplete {
        case Success(users) =>
          if (users.nonEmpty) {
            // Pull the data for these users and write to file
            println(s"processing ${users.length} users")
            process(db, users, page, dir)
            loop(page + 1)
          } else synch.continue()

        case Failure(e) =>
          // todo
          println(s"Oooops: ${e.getClass.getName} - ${e.getMessage}")
          synch.continue()
      }
    }
    loop()
    synch.pause()
  }
}
