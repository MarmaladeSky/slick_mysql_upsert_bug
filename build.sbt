val MysqlJdbcVersion = "8.0.33"
val ScalaVersion = "2.13.10"
val SlickVersion = "3.3.3"
val LogbackVersion = "1.2.6"

lazy val root = (project in file("."))
  .settings(
    organization := "com.github.marmaladesky",
    name := "mysql-upsert-bug",
    scalaVersion := ScalaVersion,
    libraryDependencies ++= Seq(
      "com.mysql" % "mysql-connector-j" % MysqlJdbcVersion,
      "com.typesafe.slick" %% "slick" % SlickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickVersion,
      "ch.qos.logback" % "logback-classic" % LogbackVersion
    )
  )