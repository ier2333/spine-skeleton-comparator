@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "REPO_DIR=%SCRIPT_DIR%.."

if "%SPINE_LIBGDX_DIR%"=="" (
  set "UPSTREAM_DIR=%REPO_DIR%\..\spine-runtimes\spine-libgdx"
) else (
  set "UPSTREAM_DIR=%SPINE_LIBGDX_DIR%"
)

set "UPSTREAM_SPINE_DIR=%UPSTREAM_DIR%\spine-skeletonviewer\src\com\esotericsoftware\spine"
set "UPSTREAM_SKIN_DIR=%UPSTREAM_DIR%\spine-skeletonviewer\assets\skin"
set "OVERLAY_SPINE_DIR=%REPO_DIR%\overlay\com\esotericsoftware\spine"
set "UPSTREAM_OVERLAY_DIR=%REPO_DIR%\upstream-overlay\spine-libgdx"

if not exist "%UPSTREAM_DIR%" (
  echo Upstream spine-libgdx directory not found: %UPSTREAM_DIR%
  echo Set SPINE_LIBGDX_DIR to your upstream spine-libgdx path.
  exit /b 1
)

if not exist "%UPSTREAM_SPINE_DIR%" mkdir "%UPSTREAM_SPINE_DIR%"
if not exist "%UPSTREAM_SKIN_DIR%" mkdir "%UPSTREAM_SKIN_DIR%"

copy /Y "%OVERLAY_SPINE_DIR%\SkeletonComparator.java" "%UPSTREAM_SPINE_DIR%\SkeletonComparator.java" >nul
copy /Y "%OVERLAY_SPINE_DIR%\SkeletonComparatorUI.java" "%UPSTREAM_SPINE_DIR%\SkeletonComparatorUI.java" >nul
copy /Y "%OVERLAY_SPINE_DIR%\SkeletonComparatorLoader.java" "%UPSTREAM_SPINE_DIR%\SkeletonComparatorLoader.java" >nul
copy /Y "%OVERLAY_SPINE_DIR%\SkeletonComparatorDiff.java" "%UPSTREAM_SPINE_DIR%\SkeletonComparatorDiff.java" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\src\com\esotericsoftware\spine\SkeletonViewerAtlas.java" "%UPSTREAM_SPINE_DIR%\SkeletonViewerAtlas.java" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\build.gradle" "%UPSTREAM_DIR%\build.gradle" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\skin.json" "%UPSTREAM_SKIN_DIR%\skin.json" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\skin.atlas" "%UPSTREAM_SKIN_DIR%\skin.atlas" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\skin.png" "%UPSTREAM_SKIN_DIR%\skin.png" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\font-calibri-12.fnt" "%UPSTREAM_SKIN_DIR%\font-calibri-12.fnt" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\font-calibri-12.png" "%UPSTREAM_SKIN_DIR%\font-calibri-12.png" >nul
copy /Y "%UPSTREAM_OVERLAY_DIR%\spine-skeletonviewer\assets\skin\NotoSansSC-Regular.otf" "%UPSTREAM_SKIN_DIR%\NotoSansSC-Regular.otf" >nul

pushd "%UPSTREAM_DIR%"
call gradlew.bat :spine-skeletonviewer:comparatorJar
if errorlevel 1 (
  popd
  exit /b 1
)
java -jar "spine-skeletonviewer\build\libs\spine-skeletonviewer-4.2.13-SNAPSHOT-comparator.jar" %*
set "EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %EXIT_CODE%
