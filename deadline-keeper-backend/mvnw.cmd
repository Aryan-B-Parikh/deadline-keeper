@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSM__=
@SET PS_MODULE_PATH=
@SET MVNW_USERNAME=
@SET MVNW_PASSWORD=

@REM ==== START VALIDATION ====
@IF NOT "%JAVA_HOME%"=="" goto OkJHome
@IF NOT "%JDK_HOME%"=="" set JAVA_HOME=%JDK_HOME%

:OkJHome
@IF NOT "%JAVA_HOME%"=="" goto checkJHome
@echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
@echo Please set the JAVA_HOME variable in your environment to match the
@echo location of your Java installation.
goto fail

:checkJHome
@IF EXIST "%JAVA_HOME%\bin\java.exe" goto init

@echo ERROR: JAVA_HOME is set to an invalid directory.
@echo JAVA_HOME = "%JAVA_HOME%"
@echo Please set the JAVA_HOME variable in your environment to match the
@echo location of your Java installation.
goto fail

:init

@REM ==== MVNW_JAVA ====
@IF NOT "%MVNW_JAVA%"=="" goto runMvnw

@REM Find project base dir
set MAVEN_PROJECTBASEDIR=%~dp0

@REM ==== DOWNLOAD MAVEN ====
@IF EXIST "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven\bin\mvn.cmd" goto runMvnw

set MAVEN_URL=
set MAVEN_HOME=%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven

@REM Read distributionUrl from maven-wrapper.properties
if not exist "%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties" goto fail
for /f "usebackq tokens=1,2 delims==" %%A in ("%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.properties") do (
    if "%%A"=="distributionUrl" set MAVEN_URL=%%B
)
if "%MAVEN_URL%"=="" goto fail

@echo Downloading Maven...
@echo From: %MAVEN_URL%

@REM Create maven directory
if not exist "%MAVEN_HOME%" mkdir "%MAVEN_HOME%"

@REM Download using PowerShell
powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_HOME%\maven.zip' }"

@REM Extract to a staging dir, then copy recursively (move with wildcards
@REM does not copy subdirectories, which breaks the layout)
powershell -Command "& { Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%\extract' -Force }"

for /d %%i in ("%MAVEN_HOME%\extract\apache-maven-*") do xcopy "%%i" "%MAVEN_HOME%\" /e /i /y /q >nul
rmdir /s /q "%MAVEN_HOME%\extract" 2>nul
del "%MAVEN_HOME%\maven.zip" 2>nul

:runMvnw
set MVNW_JAVA=%JAVA_HOME%\bin\java.exe
"%MAVEN_HOME%\bin\mvn.cmd" %*

@REM ==== END ====
goto eof

:fail
set ERROR_CODE=1

:eof
@endlocal & echo off & exit /b %ERROR_CODE%
