package com.ccfs.util

import java.io.{File, FileWriter}
import java.util.concurrent.locks.{Condition, Lock, ReentrantLock}

import au.com.bytecode.opencsv.CSVWriter
import com.ccfs.daos.UserDAO._
import com.ccfs.model.UserModel.{MixItem, User, UserMix}
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}

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


  private def mixItemToJson(mixItem: MixItem): JsObject = Json.obj(
    "bevID" -> mixItem.beverageId,
    "ratio" -> mixItem.ratio
  )

  private def mixItemsToJson(mixItems: Seq[MixItem]): Seq[JsObject] =
    mixItems.map(mixItemToJson)

  private def mixToJson(mix: UserMix, mixItems: Seq[MixItem]): JsObject = Json.obj(
    "name" -> mix.name,
    "mixItems" -> mixItemsToJson(mixItems)
  )

  private def mixesToJson(mixes: Seq[(UserMix, Seq[MixItem])]): Seq[JsObject] =
    mixes.map { case (mix, mixItems) => mixToJson(mix, mixItems) }

  private def mixesAndFavsToJson(mixes: Seq[(UserMix, Seq[MixItem])], favs: Seq[Int]): JsObject = Json.obj(
    "favorites" -> favs,
    "mixes" -> mixesToJson(mixes)
  )

  private def writeToFile(data: Seq[(Option[String], Seq[(UserMix, Seq[MixItem])], Seq[Int])], page: Int, dir: File): Unit = {
    // Map to a List of string arrays. Each component of the Array is an element to be separated with commas.
    val lines = data.toList.map { case (jrid, mixes, favs) =>
      jrid.fold(Array.empty[String])(j => Array(j, Json.stringify(mixesAndFavsToJson(mixes, favs))))
    }.filterNot(_.isEmpty)

    // Create file to write to
    val file = new File(dir, f"userdata_${page + 1}%03d.csv")
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

    println(s"Starting at: ${DateTime.now()}")

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

    println(s"Stopping at: ${DateTime.now()}")
  }

  // Temporary
  def main(args: Array[String]): Unit = {
    val mis = Seq(MixItem(Some(1), Some(30), Some(42)),
      MixItem(Some(2), Some(70), Some(42)))

    val mix = UserMix(Option("mix"), Some(11), 0, 42)

    val mixes = Seq((mix, mis))

    val favs = Seq.empty[Int]

    val res = mixesAndFavsToJson(mixes, favs)

    println(Json.stringify(res))

  }
}
