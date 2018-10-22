package com.ccfs.model

import play.api.libs.json._

import scala.language.implicitConversions


object UserModel {

  case class User
  (
    jrid: String,
    id: Int
  )

  implicit val userFormat = Json.format[User]


  case class UserFavorite(userId: Int, beverageId: Int, rank: Int)

  implicit val userFavoriteFormat = Json.format[UserFavorite]


  case class UserMix
  (
    name: String,
    userId: Int,
    rank: Int,
    id: Int
  )

  case class MixItem
  (
    beverageId: Int,
    ratio: Int,
    mixId: Int
  )

  implicit val userMixFormat = Json.format[UserMix]
  implicit val mixItemFormat = Json.format[MixItem]

}
