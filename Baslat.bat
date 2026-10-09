@echo off
chcp 65001 >nul
cd /d "%~dp0"
:menu
echo.
echo ===== Volvo S60 T3 OBD =====
echo  1 - Ariza kodlarini oku
echo  2 - Canli veri kaydet (Ctrl+C ile durdur)
echo  3 - Ariza kodlarini sil
echo  4 - COM portlarini listele
echo  5 - Demo (adaptorsuz 60 sn deneme)
echo  0 - Cikis
set /p s=Secim: 
if "%s%"=="1" python volvo_obd.py kodlar %PORT%
if "%s%"=="2" python volvo_obd.py kaydet %PORT%
if "%s%"=="3" python volvo_obd.py sil %PORT%
if "%s%"=="4" python volvo_obd.py portlar
if "%s%"=="5" python volvo_obd.py kaydet --demo --sure 60 --aralik 0.2
if "%s%"=="0" exit /b
goto menu
