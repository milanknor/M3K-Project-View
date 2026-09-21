@echo off
rem Sestaveni: mppview.exe (GUI, M3K Project View), mppread.dll (ctecka, LGPL 2.1) a mppdump.exe (testovaci konzole).
rem Vyzaduje MSVC Build Tools 2022.  build.cmd setup -> navic instalator (Inno Setup 6) a balicek zdroju mppread-src.zip
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
cd /d "%~dp0"
if not exist out mkdir out
set CL_FLAGS=/nologo /std:c++17 /EHsc /O1 /Os /MT /GL /utf-8 /W3 /DUNICODE /D_UNICODE /DNDEBUG /Fo:out\
rc /nologo /fo out\mppread.res mppread.rc
if errorlevel 1 exit /b 1
cl %CL_FLAGS% /LD mppread.cpp /Fe:out\mppread.dll /link out\mppread.res /LTCG /OPT:REF /OPT:ICF
if errorlevel 1 exit /b 1
cl %CL_FLAGS% mppdump.cpp /Fe:out\mppdump.exe /link /LTCG /OPT:REF /OPT:ICF
if errorlevel 1 exit /b 1
rc /nologo /fo out\mppview.res mppview.rc
if errorlevel 1 exit /b 1
cl %CL_FLAGS% mppview.cpp /Fe:out\mppview.exe /link out\mppview.res /LTCG /OPT:REF /OPT:ICF /SUBSYSTEM:WINDOWS
if errorlevel 1 exit /b 1
del out\*.obj out\mppread.exp out\mppread.lib >nul 2>&1
dir out\*.exe out\*.dll | findstr /i "exe dll"
if /i "%1"=="setup" (
  if exist out\mppread-src.zip del out\mppread-src.zip
  powershell -NoProfile -Command "Compress-Archive -Path cfb.h,mpp.h,mspdi.h,model.h,serial.h,mppread.h,mppread.cpp,mppread.rc,mppload.h,mppdump.cpp,build-mppread.cmd,README-mppread.txt,COPYING.LIB,LICENSE-MIT.txt -DestinationPath out\mppread-src.zip"
  if errorlevel 1 exit /b 1
  "%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe" /Q mppview.iss
  if errorlevel 1 exit /b 1
  dir out\M3K-Project-View-*-Setup.exe out\mppread-src.zip | findstr /i "exe zip"
)
exit /b 0
