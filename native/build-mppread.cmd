@echo off
rem Sestaveni knihovny mppread.dll ze zdroju (GNU LGPL 2.1). Vyzaduje MSVC Build Tools 2022 (zdarma).
rem Vysledny out\mppread.dll zkopirujte do slozky programu M3K Project View misto puvodniho souboru.
rem Rozhrani (mppread.h) a format dat (serial.h, SERIAL_VERSION) musi zustat kompatibilni.
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat" >nul 2>&1
cd /d "%~dp0"
if not exist out mkdir out
set CL_FLAGS=/nologo /std:c++17 /EHsc /O1 /Os /MT /utf-8 /W3 /DUNICODE /D_UNICODE /DNDEBUG /Fo:out\
rc /nologo /fo out\mppread.res mppread.rc
cl %CL_FLAGS% /LD mppread.cpp /Fe:out\mppread.dll /link out\mppread.res
if errorlevel 1 exit /b 1
rem volitelne: testovaci konzole (mppdump soubor.mpp, mppdump -dll soubor.mpp)
cl %CL_FLAGS% mppdump.cpp /Fe:out\mppdump.exe
exit /b 0
