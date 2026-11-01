@echo off
rem Sestaveni: mppview.exe (GUI, M3K Project View), mppread.dll (ctecka, LGPL 2.1) a mppdump.exe (testovaci konzole).
rem Vyzaduje MSVC Build Tools 2022.  build.cmd setup -> navic instalator (Inno Setup 6) a balicek zdroju mppread-src.zip
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
cd /d "%~dp0"
if not exist out mkdir out
rem /sdl = rozsirene bezpecnostni kontroly, /guard:cf = Control Flow Guard (ochrana proti zneuziti chyb v pameti)
set CL_FLAGS=/nologo /std:c++17 /EHsc /O1 /Os /MT /GL /utf-8 /W3 /sdl /guard:cf /DUNICODE /D_UNICODE /DNDEBUG /Foout\
rem /MANIFEST:EMBED = manifest (vzhled Windows, asInvoker) primo v EXE; samostatny .manifest instalator nekopiroval
set LINK_FLAGS=/LTCG /OPT:REF /OPT:ICF /guard:cf /DYNAMICBASE /NXCOMPAT /HIGHENTROPYVA /MANIFEST:EMBED
rc /nologo /fo out\mppread.res mppread.rc
if errorlevel 1 exit /b 1
cl %CL_FLAGS% /LD mppread.cpp /Feout\mppread.dll /link out\mppread.res %LINK_FLAGS%
if errorlevel 1 exit /b 1
cl %CL_FLAGS% mppdump.cpp /Feout\mppdump.exe /link %LINK_FLAGS%
if errorlevel 1 exit /b 1
rc /nologo /fo out\mppview.res mppview.rc
if errorlevel 1 exit /b 1
cl %CL_FLAGS% mppview.cpp /Feout\mppview.exe /link out\mppview.res %LINK_FLAGS% /SUBSYSTEM:WINDOWS
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
if /i not "%1"=="setup" exit /b 0
rem MSI pro firemni nasazeni (WiX Toolset 5.0.2, licence MS-RL); bez WiX se preskoci
for /f "tokens=2 delims==" %%v in ('findstr /b "AppVersion=" mppview.iss') do set "VER=%%v"
where wix >nul 2>&1 || (echo WiX nenalezen - MSI se nesestavi: dotnet tool install --global wix --version 5.0.2 & exit /b 0)
wix build mppview.wxs -arch x64 -d Version=%VER% -bindpath out -bindpath . -o out\M3K-Project-View-%VER%.msi
if errorlevel 1 exit /b 1
rem ICE38/43/57 = plane poplachy u zastupce v nabidce Start pri instalaci pro vsechny uzivatele
wix msi validate -sice ICE38 -sice ICE43 -sice ICE57 out\M3K-Project-View-%VER%.msi
if errorlevel 1 exit /b 1
del out\M3K-Project-View-%VER%.wixpdb >nul 2>&1
dir out\M3K-Project-View-%VER%.msi | findstr /i "msi"
exit /b 0
