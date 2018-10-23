package com.ccfs.model

import scala.language.implicitConversions


object UserModel {

  case class User(jrid: Option[String], id: Int)

  case class UserFavorite(userId: Int, beverageId: Int, rank: Int)

  case class UserMix(name: Option[String], userId: Option[Int], rank: Int, id: Int)

  case class MixItem(beverageId: Option[Int], ratio: Option[Int], mixId: Option[Int])

}
