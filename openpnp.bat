@echo off

rem Run from the folder this script is in, whatever folder you launched it from. The program reads
rem VERSION.txt and writes its configuration relative to the working directory, so starting it from
rem anywhere else gave an About dialog with no version in it.
cd /d "%~dp0"

set archi=%PROCESSOR_ARCHITECTURE%

if not x%archi:86=%==x%archi% java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.color=ALL-UNNAMED -jar target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar
if not x%archi:64=%==x%archi% java --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.color=ALL-UNNAMED -jar target\openpnp-gui-0.0.1-alpha-SNAPSHOT.jar
