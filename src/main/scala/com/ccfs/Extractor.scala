package com.ccfs

import java.io._

import com.ccfs.Main._
import com.ccfs.daos.UserDAO.{getUserPrefs, getUsers, _}
import com.ccfs.util.Content._
import org.joda.time.DateTime
import scalaz.\/

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object Extractor {

  type UserData = (Option[String], Seq[(UserMix, Seq[MixItem])], Seq[Int])

  import com.ccfs.Main.dbConfig.profile.api._

  // Processes one page of users. Pauses for completion to avoid depleting resources.
  private def process(db: Database, users: Seq[User], acc: List[Array[String]],
                      index: Int, dir: File): Throwable \/ (Int, List[Array[String]]) = \/.fromTryCatchNonFatal {

    // Terminate on empty user sequence
    if (users.isEmpty) {
      if (acc.nonEmpty)
        writeToFile(acc, index, dir)
      (-1, List.empty)
    }

    else {

      val f = Future.sequence(users.map(user =>
        getUserPrefs(db, user.id).map { case (mixes, favs) => (user.jrid, mixes, favs) }))

      val batch = Await.result(f, Duration(1, MINUTES))

      val lines = convert(batch)

      println(s"${lines.length}/${users.length}")

      val (left, right) = lines.splitAt(PAGE_SIZE - acc.length)

      val curr = acc ++ left

      // If we have accumulated enough entries, write to file. Otherwise, return the current index and accumulator.
      if (curr.length < PAGE_SIZE) (index, curr)
      else {
        writeToFile(curr, index, dir)
        // Return the next index and next accumulator.
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
          process(db, users, acc, index, dir).fold(e => {
            println(s"Oooops: ${e.getClass.getName} - ${e.getMessage}")
            synchronized {
              notify()
            }
          }, r => {
            val (newIndex, lines) = r
            println(s"index: $newIndex, size of next batch: ${lines.length}")
            if (newIndex != -1) loop(page + 1, newIndex, lines) else synchronized {
              notify()
            }
          })

        case Failure(e) =>
          // todo
          println(s"Oooops: ${e.getClass.getName} - ${e.getMessage}")
          synchronized {
            notify()
          }
      }
    }

    loop()

    // Wait for completion
    synchronized {
      wait()
    }

    // Zip the output
    val zipFile = new File(dir, basename + ".zip")
    zip(dir, zipFile, _.endsWith(CSV))
    println(s"Your package at ${zipFile.getPath} is ready!")

    println(s"Stopping at: ${DateTime.now()}")
  }

}
