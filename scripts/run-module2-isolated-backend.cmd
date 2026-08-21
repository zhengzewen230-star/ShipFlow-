@echo off
setlocal EnableExtensions

if "%SHIPFLOW_HTTP_TEST_DB_URL%"=="" (
  echo SHIPFLOW_HTTP_TEST_DB_URL is required. 1>&2
  exit /b 2
)
if "%SHIPFLOW_HTTP_TEST_DB_USERNAME%"=="" (
  echo SHIPFLOW_HTTP_TEST_DB_USERNAME is required. 1>&2
  exit /b 2
)
if "%SHIPFLOW_HTTP_TEST_DB_PASSWORD%"=="" (
  echo SHIPFLOW_HTTP_TEST_DB_PASSWORD is required. 1>&2
  exit /b 2
)

powershell.exe -NoProfile -Command "$url = [uri]($env:SHIPFLOW_HTTP_TEST_DB_URL -replace '^jdbc:', ''); if ($url.AbsolutePath.Trim('/').Split('?')[0] -ne 'shipflow_http_test') { exit 2 }"
if errorlevel 1 (
  echo The isolated backend target must be shipflow_http_test. 1>&2
  exit /b 2
)

set "DB_URL=%SHIPFLOW_HTTP_TEST_DB_URL%"
set "DB_USERNAME=%SHIPFLOW_HTTP_TEST_DB_USERNAME%"
set "DB_PASSWORD=%SHIPFLOW_HTTP_TEST_DB_PASSWORD%"
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"

call "D:\Programs\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd" org.springframework.boot:spring-boot-maven-plugin:3.5.16:run
