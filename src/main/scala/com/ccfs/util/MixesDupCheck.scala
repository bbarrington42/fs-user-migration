package com.ccfs.util

import java.io.{File, FileInputStream, InputStream}

import play.api.libs.json._

object MixesDupCheck {

  // Implicit conversion so we can use .as[MixItem] ...
  implicit val readsMixItem = new Reads[MixItem] {
    override def reads(json: JsValue): JsResult[MixItem] = {
      val map = json.as[JsObject].value
      JsSuccess(MixItem(map("bevID").as[Int], map("ratio").as[Int]))
    }
  }

  // Implicit conversion so we can use .as[Mix] ...
  implicit val readsMix = new Reads[Mix] {
    override def reads(json: JsValue): JsResult[Mix] = {
      val map = json.as[JsObject].value
      val mixItems = map("mixItems").as[JsArray].value.map(_.as[MixItem])
      JsSuccess(Mix(map("name").as[String], mixItems))
    }
  }

  // Class representations for JSON counterparts
  case class MixItem(bevId: Int, ratio: Int)

  case class Mix(name: String, mixItems: Seq[MixItem]) {

    // Override equals and hashCode so that the mix name is not considered for equality
    override def equals(obj: Any): Boolean = obj match {
      case Mix(_, items) => items == mixItems
      case _ => false
    }

    override def hashCode(): Int = mixItems.##
  }

  // Recursively search for duplicates within the mixes array, removing them and capturing their names in a map
  def findDups(mixes: List[Mix], acc: Map[Int, Seq[String]]): Map[Int, Seq[String]] = mixes match {
    case Nil => acc
    case head :: tail =>
      // Partition based on equality to head item
      val (equal, rest) = tail.partition(_ == head)
      // Be sure to include the head item in the collection of names
      findDups(rest, acc.updated(head.hashCode(), (head :: equal).map(_.name)))
  }

  def transform(json: InputStream): List[Mix] =
  // Parse the JSON and transform to a list of Mix instances
    Json.parse(json).as[JsObject].value("mixes").as[JsArray].value.map(_.as[Mix]).toList


  def main(args: Array[String]): Unit = {
    val file = new File("data/beverages-check-for-dups.json")

    val result = findDups(transform(new FileInputStream(file)), Map.empty)

    result.foreach { case (key, value) => println(s"hash: ${key} -> names: ${value.mkString(", ")}") }

    println()

    println(s"Number of unique mixes: ${result.keys.toList.length}")
  }
}
