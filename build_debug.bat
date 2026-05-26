@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo ========================================
echo Building debug APK...
echo ========================================
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [FAILED] Build failed with errors.
    exit /b 1
)
echo.
echo [SUCCESS] Build completed successfully.
