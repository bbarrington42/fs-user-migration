package com.ccfs.util

import java.io._
import java.nio.charset.StandardCharsets
import java.util.zip.{ZipEntry, ZipOutputStream}

import com.ccfs.Extractor.UserData
import com.ccfs.Main.{CSV, basename}
import com.ccfs._
import com.ccfs.daos.UserDAO.{MixItem, UserMix}
import com.opencsv.CSVWriter
import play.api.libs.json.{JsObject, Json}

import scala.collection.JavaConverters._

object Content {

  private def isPrintableAscii(b: Byte): Boolean = 31 < b && 127 > b

  private def sanitize(name: String): String =
    new String(name.getBytes(StandardCharsets.UTF_8).
      filter(isPrintableAscii), StandardCharsets.UTF_8)


  // Conversion to JSON
  private def mixItemToJson(mixItem: MixItem): JsObject = Json.obj(
    "bevID" -> mixItem.beverageId,
    "ratio" -> mixItem.ratio
  )

  private def mixItemsToJson(mixItems: Seq[MixItem]): Seq[JsObject] = mixItems.map(mixItemToJson)

  private def mixToJson(mix: UserMix, mixItems: Seq[MixItem]): JsObject = Json.obj(
    // Remove any non-printable ascii characters
    "name" -> mix.name.map(sanitize),
    "mixItems" -> mixItemsToJson(mixItems)
  )

  private def mixesToJson(mixes: Seq[(UserMix, Seq[MixItem])]): Seq[JsObject] =
    mixes.map { case (mix, mixItems) => mixToJson(mix, mixItems) }

  private def mixesAndFavsToJson(mixes: Seq[(UserMix, Seq[MixItem])], favs: Seq[Int]): JsObject = Json.obj(
    "favorites" -> favs,
    "mixes" -> mixesToJson(mixes)
  )

  private def deDup(lines: List[Array[String]]): List[Array[String]] = lines match {
    case Nil => Nil
    case head :: tail => head :: deDup(tail.filterNot(_ == head))
  }

  def writeToFile(lines: List[Array[String]], index: Int, parent: File): Unit = {

    // Kludge - Remove any duplicates
    val deduped = deDup(lines)

    if(deduped.length != lines.length)
      println(s"Duplicate(s) found: index=${index}!")

    // Create file to write to
    val file = new File(parent, f"${basename}_$index%03d.$CSV")
    println(s"Writing ${deduped.length} lines to ${file.getName}")

    val csv = new CSVWriter(new FileWriter(file))
    try {
      csv.writeAll(deduped.asJava)
    } finally {
      csv.close()
    }
  }

  // Map to a List of string arrays. Each component of the Array is an element to be separated with commas.
  // If the jrid is missing or if the mixes AND favorites are both empty, then filter them out.
  def convert(userData: Seq[UserData]): List[Array[String]] =
    userData.toList.map { case (jrid, mixes, favs) =>
      if (mixes.isEmpty && favs.isEmpty) Array.empty[String] else
        jrid.fold(Array.empty[String])(j => Array(j, Json.stringify(mixesAndFavsToJson(mixes, favs))))
    }.filterNot(_.isEmpty)

  // Zip all files in a directory that match the file name filter. No recursion.
  def zip(dir: File, zipFile: File, filter: String => Boolean): Unit = {
    val bytes = Array.fill[Byte](1000)(0)

    def xfr(in: InputStream, out: OutputStream): Unit = {

      def loop(len: Int): Unit = {
        if (-1 != len) {
          out.write(bytes, 0, len)
          loop(in.read(bytes))
        }
      }

      loop(in.read(bytes))
    }

    def zip(zos: ZipOutputStream, file: File): ZipOutputStream = {
      val fis = new FileInputStream(file)

      try {
        val entry = new ZipEntry(file.getName)
        zos.putNextEntry(entry)
        xfr(fis, zos)
      } finally fis.close()

      zos
    }

    val out = new ZipOutputStream(new FileOutputStream(zipFile))
    try {
      val files = dir.listFiles(filter)
      files.foldLeft(out)((o, f) => zip(o, f))
    } finally out.close()
  }
}
