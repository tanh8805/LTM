@echo off
cd /d "%~dp0"
java -Dexam.debug=true -Dexam.allowTerminal=true -cp exam.jar exam.client.StudentApp
pause
