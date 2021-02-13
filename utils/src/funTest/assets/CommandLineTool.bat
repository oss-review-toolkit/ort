@echo off

if "%1" == "--get-version" (
    echo The version is 1.2.3
    exit /b 123
)

echo Current directory: %cd%
echo Path to script: %~f0
echo Hello to stderr! 1>&2

exit /b 42
