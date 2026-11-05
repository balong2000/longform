@echo off
rem Builds Longform with the project-local toolchain in .tools\ (nothing is
rem installed system-wide). Usage:  build.cmd assembleRelease   or   build.cmd test
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%.tools\jdk"
set "ANDROID_HOME=%ROOT%.tools\sdk"
set "GRADLE_USER_HOME=%ROOT%.tools\gradle-home"
rem Antivirus HTTPS filtering can break Java's TLS; trust the Windows certificate store.
set "GRADLE_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%ROOT%.tools\gradle-9.8.2\bin\gradle.bat" -p "%ROOT%." --console=plain %*
