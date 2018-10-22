package com.ccfs.daos

import com.ccfs.model.UserModel.{MixItem, User, UserFavorite, UserMix}
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global


object UserDAO extends DatabaseConfig[JdbcProfile] {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  import profile.api._


  private[daos] class Users(tag: Tag) extends Table[User](tag, "FreestyleUser") {

    def jrid = column[String]("jrid")

    def id = column[Int]("id", O.PrimaryKey)

    def * = (jrid, id) <> (User.tupled, User.unapply)
  }

  private[daos] class UserFavorites(tag: Tag) extends Table[UserFavorite](tag, "UserFavorite") {
    def userId = column[Int]("userId")

    def beverageId = column[Int]("beverageId")

    def rank = column[Int]("rank")

    def * = (userId, beverageId, rank) <> (UserFavorite.tupled, UserFavorite.unapply)
  }

  private[daos] class UserMixes(tag: Tag) extends Table[UserMix](tag, "UserMixes") {

    def name = column[String]("name")

    def userId = column[Int]("userId")

    def rank = column[Int]("rank")

    def id = column[Int]("id", O.PrimaryKey)

    def * = (name, userId, rank, id) <> (UserMix.tupled, UserMix.unapply)
  }

  private[daos] class MixItems(tag: Tag) extends Table[MixItem](tag, "MixItems") {
    def beverageId = column[Int]("beverageId")

    def ratio = column[Int]("ratio")

    def mixId = column[Int]("mixId")

    def * = (beverageId, ratio, mixId) <> (MixItem.tupled, MixItem.unapply)
  }

  private[daos] val users = TableQuery[Users]
  private[daos] val userFavorites = TableQuery[UserFavorites]

  private[daos] val userMixes = TableQuery[UserMixes]
  private[daos] val mixItems = TableQuery[MixItems]


  def selectUserMixes(userId: Int): DBIO[Seq[UserMix]] = userMixes.filter(_.userId === userId).sortBy(_.rank).result

  def selectMixItem(mixId: Int): DBIO[Seq[MixItem]] = mixItems.filter(_.mixId === mixId).result

  def selectMixItems(mixIds: Seq[Int]): DBIO[Seq[Seq[MixItem]]] = DBIO.sequence(mixIds.map(selectMixItem))

  def getFavorites(userId: Int): Future[Seq[Int]] = {
    val q = userFavorites.filter(_.userId === userId).sortBy(_.rank).result
    val action = q.map(s => s.map { case UserFavorite(_, bevId, _) => bevId })
    db.run(action)
  }

  def getUserMixes(userId: Int): Future[Seq[(UserMix, Seq[MixItem])]] = {
    val action = for {
      mixes <- selectUserMixes(userId)
      mixIds = mixes.map(_.id)
      items <- selectMixItems(mixIds)
    } yield mixes zip items
    db.run(action)
  }

  def getUserSelections(userId: Int): Future[(Seq[(UserMix, Seq[MixItem])], Seq[Int])] = for {
    mixes <- getUserMixes(userId)
    favs <- getFavorites(userId)
  } yield (mixes, favs)


}
