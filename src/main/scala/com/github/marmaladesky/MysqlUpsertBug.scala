package com.github.marmaladesky

import slick.jdbc.JdbcBackend
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object MysqlUpsertBug {

  private val db: JdbcBackend#DatabaseDef = Database.forConfig("mysql")

  case class AnimalRow(id: Int, name: String, location: String)
  class Animals(tag: Tag) extends Table[AnimalRow](tag, "animals") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("name", O.Unique, O.Length(100))
    def location = column[String]("location")
    def * = (id, name, location) <> (AnimalRow.tupled, AnimalRow.unapply)
  }
  val animals = TableQuery[Animals]

  private def init(): Future[Unit] = {
    db.run {
      DBIO.seq(
        animals.schema.dropIfExists,
        animals.schema.create
      )
    }
  }

  def main(args: Array[String]): Unit = {
    val f = for {
      _ <- init()

      tigerRow = AnimalRow(0, "tiger", "NY")
      _ <- db.run { animals.insertOrUpdate(tigerRow) }
      tigerAfterInsert <- db.run { animals.filter(_.name === "tiger").result.head }

      movedTiger = tigerRow.copy(location = "CA")
      _ <- db.run { animals.insertOrUpdate(movedTiger) }
      tigerAfterUpsertByUniqueName <- db.run { animals.filter(_.name === "tiger").result.head }

    } yield assert(tigerAfterInsert.id == tigerAfterUpsertByUniqueName.id)

    Await.result(f, Duration.Inf)
  }

}
