@echo off
setlocal
set "MAVEN_CMD=mvn"
if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if defined JAVA_HOME set "MAVEN_JAVA_HOME=%JAVA_HOME%"
if defined MAVEN_JAVA_HOME set "JAVA_HOME=%MAVEN_JAVA_HOME%"
call "%MAVEN_CMD%" %*
exit /b %ERRORLEVEL%
