@echo off
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
set KOTLIN_COMPILER_EXECUTION_STRATEGY=in-process
cd /d D:\kaifa\GameCenterApp
call gradlew.bat assembleDebug -Dkotlin.compiler.execution.strategy=in-process --no-daemon
echo BUILD_EXIT_CODE=%ERROR_LEVEL%
pause
