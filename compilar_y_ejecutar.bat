@echo off
rem Compila y ejecuta TecnoMovil Data. Uso: compilar_y_ejecutar.bat [usuarios] [parte: 1, 2 o todo]
chcp 65001 > nul
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 --release 17 -d out -sourcepath src src\tecnomovil\Main.java src\tecnomovil\verificacion\VerificacionFunciones.java
if errorlevel 1 exit /b 1
java -Xmx6g -Dstdout.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -cp out tecnomovil.Main %*
