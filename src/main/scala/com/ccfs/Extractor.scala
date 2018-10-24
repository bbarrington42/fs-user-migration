package com.ccfs

import java.io.{File, FileWriter}

import com.ccfs.Main._
import com.ccfs.daos.UserDAO.{getUserPrefs, getUsers, _}
import com.opencsv.CSVWriter
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object Extractor {

  type UserData = (Option[String], Seq[(UserMix, Seq[MixItem])], Seq[Int])

  import com.ccfs.Main.dbConfig.profile.api._

  def using(db: Database)(f: Database => Unit): Unit = try f(db) finally db.close

  // Conversion to JSON
  private def mixItemToJson(mixItem: MixItem): JsObject = Json.obj(
    "bevID" -> mixItem.beverageId,
    "ratio" -> mixItem.ratio
  )

  private def mixItemsToJson(mixItems: Seq[MixItem]): Seq[JsObject] = mixItems.map(mixItemToJson)

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

  private def writeToFile(lines: List[Array[String]], index: Int, dir: File): Unit = {

    // Create file to write to
    val file = new File(dir, f"userdata_$index%03d.csv")
    println(s"Writing ${lines.length} lines to ${file.getName}")

    val csv = new CSVWriter(new FileWriter(file))
    try {
      csv.writeAll(lines.asJava)
    } finally {
      csv.close()
    }
  }

  // Map to a List of string arrays. Each component of the Array is an element to be separated with commas.
  // If the jrid is missing or if the mixes AND favorites are both empty, then filter them out.
  private def convert(userData: Seq[UserData]): List[Array[String]] =
    userData.toList.map { case (jrid, mixes, favs) =>
      if (mixes.isEmpty && favs.isEmpty) Array.empty[String] else
        jrid.fold(Array.empty[String])(j => Array(j, Json.stringify(mixesAndFavsToJson(mixes, favs))))
    }.filterNot(_.isEmpty)

  // Processes one page of users. Pauses for completion to avoid depleting resources.
  private def process(db: Database, users: Seq[User], acc: List[Array[String]], index: Int, dir: File): (Int, List[Array[String]]) = {

    // Terminate on empty user sequence
    if (users.isEmpty) {
      if (acc.nonEmpty)
        writeToFile(acc, index, dir)
      (-1, List.empty)
    }

    else {

      val f = Future.sequence(users.map(user =>
        getUserPrefs(db, user.id).map { case (mixes, favs) => (user.jrid, mixes, favs) }))

      val batch = Await.result(f, Duration.Inf)

      val lines = convert(batch)

      println(s"converted: ${lines.length}")

      // If we have accumulated enough entries, write to file
      val (left, right) = lines.splitAt(PAGE_SIZE - acc.length)

      val curr = acc ++ left

      if (curr.length < PAGE_SIZE) (index, curr)
      else {
        writeToFile(curr, index, dir)
        (index + 1, right)
      }
    }

  }

  def run(db: Database, dir: File): Unit = {

    println(s"Starting at: ${DateTime.now()}")

    def loop(page: Int = 0, index: Int = 1, acc: List[Array[String]] = List.empty): Unit = {
      getUsers(db, page).onComplete {
        case Success(users) =>
          // Pull the data for these users and write to file
          println(s"processing ${users.length} users")
          val (newIndex, lines) = process(db, users, acc, index, dir)
          println(s"index: $newIndex, current accumulated users: ${lines.length}")
          if (newIndex != -1) loop(page + 1, newIndex, lines) else notify()

        case Failure(e) =>
          // todo
          println(s"Oooops: ${e.getClass.getName} - ${e.getMessage}")
          notify()
      }
    }

    loop()

    // Wait for completion
    synchronized {
      wait()
    }

    println(s"Stopping at: ${DateTime.now()}")
  }
}
