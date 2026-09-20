@echo off
set "JAVA_HOME=%~dp0jdk17\\jdk-17.0.20.1+1"
set "PATH=%JAVA_HOME%\\bin;%PATH%"
"%~dp0maven\\apache-maven-3.9.6\\bin\\mvn.cmd" %*
