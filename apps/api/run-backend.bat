@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "DB_PORT=5433"
set "MAVEN_OPTS=-Xms128m -Xmx512m"
echo Starting Foodie Backend using %JAVA_HOME%...
call .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local" "-Dspring-boot.run.jvmArguments=-Xms256m -Xmx768m"


