@echo off
rem Runs Maven for this project, e.g.  run.bat spring-boot:run   or   run.bat test
rem Uses the bundled JDK/Maven when present (offline setup), otherwise the system JDK + Maven wrapper.
setlocal
set "HERE=%~dp0"

if exist "%HERE%jdk17\jdk-17.0.20.1+1\bin\java.exe" (
    set "JAVA_HOME=%HERE%jdk17\jdk-17.0.20.1+1"
    set "PATH=%HERE%jdk17\jdk-17.0.20.1+1\bin;%PATH%"
)

if exist "%HERE%maven\apache-maven-3.9.6\bin\mvn.cmd" (
    call "%HERE%maven\apache-maven-3.9.6\bin\mvn.cmd" -f "%HERE%pom.xml" %*
) else (
    call "%HERE%mvnw.cmd" -f "%HERE%pom.xml" %*
)
exit /b %ERRORLEVEL%
