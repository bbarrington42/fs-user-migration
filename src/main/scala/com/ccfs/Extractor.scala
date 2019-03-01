package com.ccfs

import java.io._
import java.time.Instant

import com.ccfs.Main._
import com.ccfs.daos.UserDAO.{getUserPrefs, getUsers, _}
import com.ccfs.util.Content._
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

      val (left, right) = lines.splitAt(PAGE_SIZE - acc.length)

      val curr = acc ++ left

      println(s"${left.length}/${curr.length}")

      // If we have accumulated enough entries, write to file. Otherwise, return the current index and accumulator.
      if (curr.length < PAGE_SIZE) (index, curr)
      else {
        writeToFile(curr, index, dir)
        // Return the next index and next accumulator.
        (index + 1, right)
      }
    }

  }

  private def toHHMMSS(seconds: Long): String = {
    def loop(rem: Long, acc: List[Long]): String = {
      if (acc.length == 3) acc.map(l => f"$l%02d").mkString(":") else
        loop(rem / 60, rem % 60 :: acc)
    }

    loop(seconds, Nil)
  }

  def run(db: Database, dir: File): Unit = {

    val start = Instant.now()

    println(s"Starting at: $start")

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

    val end = Instant.now()
    println(s"Stopping at: ${end}")

    val duration = java.time.Duration.between(start, end)

    println(s"Elapsed time: ${toHHMMSS(duration.getSeconds)}")
  }

}
