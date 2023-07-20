docker run -p 3306:3306 --name some-mysql \
-e MYSQL_ROOT_PASSWORD=my-secret-pw \
-e MYSQL_DATABASE=db \
-e MYSQL_USER=user \
-e MYSQL_PASSWORD=pass \
-d mysql/mysql-server:latest

sbt 'runMain com.github.marmaladesky.MysqlUpsertBug'

docker stop mysql_test
docker rm mysql_test