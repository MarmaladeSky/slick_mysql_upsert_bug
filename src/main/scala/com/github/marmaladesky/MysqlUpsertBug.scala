package com.github.marmaladesky

import slick.jdbc.JdbcBackend
import slick.jdbc.MySQLProfile.api._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object MysqlUpsertBug {

  private val db: JdbcBackend#DatabaseDef = Database.forConfig("mysql")

  //  create table A(id1 int, id2 int, primary key(id1, id2));
  case class ARow(id1: Int, id2: Int)
  class A(tag: Tag) extends Table[ARow](tag, "A") {
    def id1 = column[Int]("id1")
    def id2 = column[Int]("id2")
    val pk = primaryKey("a_pk", (id1, id2))
    def * = (id1, id2) <> (ARow.tupled, ARow.unapply)
  }
  val a = TableQuery[A]

  //  create table B(id1 int, id2 int, v int, primary key(id1, id2));
  case class BRow(id1: Int, id2: Int, v: Int)
  class B(tag: Tag) extends Table[BRow](tag, "B") {
    def id1 = column[Int]("id1")
    def id2 = column[Int]("id2")
    def v = column[Int]("v")
    val pk = primaryKey("b_pk", (id1, id2))
    def * = (id1, id2, v) <> (BRow.tupled, BRow.unapply)
  }
  val b = TableQuery[B]

  //  create table C(id1 int auto_increment, id2 int, primary key(id1, id2));
  case class CRow(id1: Int, id2: Int)
  class C(tag: Tag) extends Table[CRow](tag, "C") {
    def id1 = column[Int]("id1", O.AutoInc)
    def id2 = column[Int]("id2")
    val pk = primaryKey("c_pk", (id1, id2))
    def * = (id1, id2) <> (CRow.tupled, CRow.unapply)
  }
  val c = TableQuery[C]

  //  create table D(id1 int auto_increment, id2 int, v int, primary key(id1, id2));
  case class DRow(id1: Int, id2: Int, v: Int)
  class D(tag: Tag) extends Table[DRow](tag, "D") {
    def id1 = column[Int]("id1", O.AutoInc)
    def id2 = column[Int]("id2")
    def v = column[Int]("v")
    val pk = primaryKey("d_pk", (id1, id2))
    def * = (id1, id2, v) <> (DRow.tupled, DRow.unapply)
  }
  val d = TableQuery[D]

  //  create table E(id int primary key);
  case class ERow(id1: Int)
  class E(tag: Tag) extends Table[ERow](tag, "E") {
    def id1 = column[Int]("id1", O.PrimaryKey)
    def * = id1 <> (ERow.apply, ERow.unapply)
  }
  val e = TableQuery[E]

  //  create table F(id int primary key auto_increment);
  case class FRow(id1: Int)
  class F(tag: Tag) extends Table[FRow](tag, "F") {
    def id1 = column[Int]("id1", O.PrimaryKey, O.AutoInc)
    def * = (id1) <> (FRow.apply, FRow.unapply)
  }
  val f = TableQuery[F]

  private def init(): DBIOAction[Unit, NoStream, Effect.Schema] = {
      DBIO.seq(
        // for raw SQL see bug https://github.com/slick/slick/issues/1437
        sqlu"DROP TABLE A;", a.schema.create,
        sqlu"DROP TABLE B;", b.schema.create,
        sqlu"DROP TABLE C;", sqlu"CREATE TABLE C(id1 INT AUTO_INCREMENT, id2 INT, PRIMARY KEY(id1, id2));",
        sqlu"DROP TABLE D;", sqlu"CREATE TABLE D(id1 INT AUTO_INCREMENT, id2 INT, v INT, PRIMARY KEY(id1, id2));",
        sqlu"DROP TABLE E;", e.schema.create,
        sqlu"DROP TABLE F;", f.schema.create
      )
  }

  def main(args: Array[String]): Unit = {
    val q = for {
      _ <- init()
      //  insert into A values (0, 0) on duplicate key update id1=VALUES(id1), id2=VALUES(id2); -- (0, 0)
      _ <- a.insertOrUpdate(ARow(0, 0))
      _ <- a.result.map { rows => assert { rows == Seq(ARow(0, 0)) } }
      //  insert into A values (0, 0) on duplicate key update id1=VALUES(id1), id2=VALUES(id2); -- (0, 0)
      _ <- a.insertOrUpdate(ARow(0, 0))
      _ <- a.result.map { rows => assert { rows == Seq(ARow(0, 0)) } }

      //  insert into B values (0, 0, 1) on duplicate key update id1=VALUES(id1), id2=VALUES(id2), v=VALUES(v); -- (0,0,1)
      _ <- b.insertOrUpdate(BRow(0, 0, 1))
      _ <- b.result.map { rows => assert { rows == Seq(BRow(0, 0, 1)) } }
      //  insert into B values (0, 0, 2) on duplicate key update id1=VALUES(id1), id2=VALUES(id2), v=VALUES(v); -- (0,0,2)
      _ <- b.insertOrUpdate(BRow(0, 0, 2))
      _ <- b.result.map { rows => assert { rows == Seq(BRow(0, 0, 2)) } }
      //  insert into B values (1, 0, 2) on duplicate key update id1=VALUES(id1), id2=VALUES(id2), v=VALUES(v); -- (0,0,2), (1,0,2)
      _ <- b.insertOrUpdate(BRow(1, 0, 2))
      _ <- b.result.map { rows => assert { rows.sortBy(_.id1) == Seq(BRow(0, 0, 2), BRow(1, 0, 2)) } }

      //  insert into C(id1, id2) values (0, 1) on duplicate key update id2=VALUES(id2); -- (1, 1)
      _ <- c.insertOrUpdate(CRow(0, 1))
      _ <- c.result.map { rows => assert { rows == Seq(CRow(1, 1)) } }
      //  insert into C(id1, id2) values (0, 1) on duplicate key update id2=VALUES(id2); -- (1, 1), (2, 1)
      _ <- c.insertOrUpdate(CRow(0, 1))
      _ <- c.result.map { rows => assert { rows.sortBy(_.id1) == Seq(CRow(1, 1), CRow(2, 1)) } }

      //  insert into D values (0, 0, 1) on duplicate key update id2=VALUES(id2), v=VALUES(v); -- (1,0,1)
      _ <- d.insertOrUpdate(DRow(0, 0, 1))
      _ <- d.result.map { rows => assert { rows == Seq(DRow(1, 0, 1)) } }
      //  insert into D values (0, 0, 2) on duplicate key update id2=VALUES(id2), v=VALUES(v); -- (1,0,1),(2,0,2)
      _ <- d.insertOrUpdate(DRow(0, 0, 2))
      _ <- d.result.map { rows => assert { rows.sortBy(_.id1) == Seq(DRow(1, 0, 1), DRow(2, 0, 2)) } }
      //  insert into D values (0, 0, 2) on duplicate key update id2=VALUES(id2), v=VALUES(v); -- (1,0,1),(2,0,2),(3,0,2)
      _ <- d.insertOrUpdate(DRow(0, 0, 2))
      _ <- d.result.map { rows => assert { rows.sortBy(_.id1) == Seq(DRow(1, 0, 1), DRow(2, 0, 2), DRow(3, 0, 2)) } }
      //  insert into D values (1, 0, 3) on duplicate key update id2=VALUES(id2), v=VALUES(v); -- (1,0,3),(2,0,2),(3,0,2)
      _ <- d.insertOrUpdate(DRow(1, 0, 3))
      _ <- d.result.map { rows => assert { rows.sortBy(_.id1) == Seq(DRow(1, 0, 3), DRow(2, 0, 2), DRow(3, 0, 2)) } }

      //  insert into E values (0) on duplicate key update id = VALUES(id); -- (0)
      _ <- e.insertOrUpdate(ERow(0))
      _ <- e.result.map { rows => assert { rows == Seq(ERow(0)) } }
      //  insert into E values (0) on duplicate key update id = VALUES(id); -- (0)
      _ <- e.insertOrUpdate(ERow(0))
      _ <- e.result.map { rows => assert { rows == Seq(ERow(0)) } }

      //  insert into F values (0); -- (1)
      _ <- f.insertOrUpdate(FRow(0))
      _ <- f.result.map { rows => assert { rows == Seq(FRow(1)) } }
      //  insert into F values (0); -- (1), (2)
      _ <- f.insertOrUpdate(FRow(0))
      _ <- f.result.map { rows => assert { rows.sortBy(_.id1) == Seq(FRow(1), FRow(2)) } }
    } yield ()

    Await.result(db.run(q), Duration.Inf)
  }

}
