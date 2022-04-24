@echo off
rem This file is part of Batect.
rem Do not modify this file. It will be overwritten next time you upgrade Batect.
rem You should commit this file to version control alongside the rest of your project. It should not be installed globally.
rem For more information, visit https://github.com/batect/batect.

setlocal EnableDelayedExpansion

set "version=0.79.1"

if "%BATECT_CACHE_DIR%" == "" (
    set "BATECT_CACHE_DIR=%USERPROFILE%\.batect\cache"
)

set "rootCacheDir=!BATECT_CACHE_DIR!"
set "cacheDir=%rootCacheDir%\%version%"
set "ps1Path=%cacheDir%\batect-%version%.ps1"

set script=Set-StrictMode -Version 2.0^

$ErrorActionPreference = 'Stop'^

^

$Version='0.79.1'^

^

function getValueOrDefault($value, $default) {^

    if ($value -eq $null) {^

        $default^

    } else {^

        $value^

    }^

}^

^

$DownloadUrlRoot = getValueOrDefault $env:BATECT_DOWNLOAD_URL_ROOT "https://updates.batect.dev/v1/files"^

$UrlEncodedVersion = [Uri]::EscapeDataString($Version)^

$DownloadUrl = getValueOrDefault $env:BATECT_DOWNLOAD_URL "$DownloadUrlRoot/$UrlEncodedVersion/batect-$UrlEncodedVersion.jar"^

$ExpectedChecksum = getValueOrDefault $env:BATECT_DOWNLOAD_CHECKSUM '8d7de395863cddecc660933fa05d67af54129b06a7fea2307e409c7cd3c04686'^

^

$RootCacheDir = getValueOrDefault $env:BATECT_CACHE_DIR "$env:USERPROFILE\.batect\cache"^

$VersionCacheDir = "$RootCacheDir\$Version"^

$JarPath = "$VersionCacheDir\batect-$Version.jar"^

$DidDownload = 'false'^

^

function main() {^

    if (-not (haveVersionCachedLocally)) {^

        download^

        $DidDownload = 'true'^

    }^

^

    checkChecksum^

    runApplication @args^

}^

^

function haveVersionCachedLocally() {^

    Test-Path $JarPath^

}^

^

function download() {^

    Write-Output "Downloading Batect version $Version from $DownloadUrl..."^

^

    createCacheDir^

^

    $oldProgressPreference = $ProgressPreference^

^

    try {^

        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12^

^

        # Turn off the progress bar to significantly reduce download times - see https://github.com/PowerShell/PowerShell/issues/2138#issuecomment-251165868^

        $ProgressPreference = 'SilentlyContinue'^

^

        Invoke-WebRequest -Uri $DownloadUrl -OutFile $JarPath ^| Out-Null^

    } catch {^

        $Message = $_.Exception.Message^

^

        Write-Host -ForegroundColor Red "Downloading failed with error: $Message"^

        exit 1^

    } finally {^

        $ProgressPreference = $oldProgressPreference^

    }^

}^

^

function checkChecksum() {^

    $localChecksum = (Get-FileHash -Algorithm 'SHA256' $JarPath).Hash.ToLower()^

^

    if ($localChecksum -ne $expectedChecksum) {^

        Write-Host -ForegroundColor Red "The downloaded version of Batect does not have the expected checksum. Delete '$JarPath' and then re-run this script to download it again."^

        exit 1^

    }^

}^

^

function createCacheDir() {^

    if (-not (Test-Path $VersionCacheDir)) {^

        New-Item -ItemType Directory -Path $VersionCacheDir ^| Out-Null^

    }^

}^

^

function runApplication() {^

    $java = findJava^

    $javaVersion = checkJavaVersion $java^

^

    if ($javaVersion.Major -ge 9) {^

        $javaArgs = @("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")^

    } else {^

        $javaArgs = @()^

    }^

^

    $combinedArgs = $javaArgs + @("-Djava.net.useSystemProxies=true", "-jar", $JarPath) + $args^

    $env:HOSTNAME = $env:COMPUTERNAME^

    $env:BATECT_WRAPPER_CACHE_DIR = $RootCacheDir^

    $env:BATECT_WRAPPER_DID_DOWNLOAD = $DidDownload^

^

    $info = New-Object System.Diagnostics.ProcessStartInfo^

    $info.FileName = $java.Source^

    $info.Arguments = combineArgumentsToString($combinedArgs)^

    $info.RedirectStandardError = $false^

    $info.RedirectStandardOutput = $false^

    $info.UseShellExecute = $false^

^

    $process = New-Object System.Diagnostics.Process^

    $process.StartInfo = $info^

    $process.Start() ^| Out-Null^

    $process.WaitForExit()^

^

    exit $process.ExitCode^

}^

^

function useJavaHome() {^

    return ($env:JAVA_HOME -ne $null)^

}^

^

function findJava() {^

    if (useJavaHome) {^

        $java = Get-Command "$env:JAVA_HOME\bin\java" -ErrorAction SilentlyContinue^

^

        if ($java -eq $null) {^

            Write-Host -ForegroundColor Red "JAVA_HOME is set to '$env:JAVA_HOME', but there is no Java executable at '$env:JAVA_HOME\bin\java.exe'."^

            exit 1^

        }^

^

        return $java^

    }^

^

    $java = Get-Command "java" -ErrorAction SilentlyContinue^

^

    if ($java -eq $null) {^

        Write-Host -ForegroundColor Red "Java is not installed or not on your PATH. Please install it and try again."^

        exit 1^

    }^

^

    return $java^

}^

^

function checkJavaVersion([System.Management.Automation.CommandInfo]$java) {^

    $versionInfo = getJavaVersionInfo $java^

    $rawVersion = getJavaVersion $versionInfo^

    $parsedVersion = New-Object Version -ArgumentList $rawVersion^

    $minimumVersion = "1.8"^

^

    if ($parsedVersion -lt (New-Object Version -ArgumentList $minimumVersion)) {^

        if (useJavaHome) {^

            Write-Host -ForegroundColor Red "The version of Java that is available in JAVA_HOME is version $rawVersion, but version $minimumVersion or greater is required."^

            Write-Host -ForegroundColor Red "If you have a newer version of Java installed, please make sure JAVA_HOME is set correctly."^

            Write-Host -ForegroundColor Red "JAVA_HOME takes precedence over any versions of Java available on your PATH."^

        } else {^

            Write-Host -ForegroundColor Red "The version of Java that is available on your PATH is version $rawVersion, but version $minimumVersion or greater is required."^

            Write-Host -ForegroundColor Red "If you have a newer version of Java installed, please make sure your PATH is set correctly."^

        }^

^

        exit 1^

    }^

^

    if (-not ($versionInfo -match "64\-[bB]it")) {^

        if (useJavaHome) {^

            Write-Host -ForegroundColor Red "The version of Java that is available in JAVA_HOME is a 32-bit version, but Batect requires a 64-bit Java runtime."^

            Write-Host -ForegroundColor Red "If you have a 64-bit version of Java installed, please make sure JAVA_HOME is set correctly."^

            Write-Host -ForegroundColor Red "JAVA_HOME takes precedence over any versions of Java available on your PATH."^

        } else {^

            Write-Host -ForegroundColor Red "The version of Java that is available on your PATH is a 32-bit version, but Batect requires a 64-bit Java runtime."^

            Write-Host -ForegroundColor Red "If you have a 64-bit version of Java installed, please make sure your PATH is set correctly."^

        }^

^

        exit 1^

    }^

^

    return $parsedVersion^

}^

^

function getJavaVersionInfo([System.Management.Automation.CommandInfo]$java) {^

    $info = New-Object System.Diagnostics.ProcessStartInfo^

    $info.FileName = $java.Source^

    $info.Arguments = "-version"^

    $info.RedirectStandardError = $true^

    $info.RedirectStandardOutput = $true^

    $info.UseShellExecute = $false^

^

    $process = New-Object System.Diagnostics.Process^

    $process.StartInfo = $info^

    $process.Start() ^| Out-Null^

    $process.WaitForExit()^

^

    $stderr = $process.StandardError.ReadToEnd()^

    return $stderr^

}^

^

function getJavaVersion([String]$versionInfo) {^

    $versionLine = ($versionInfo -split [Environment]::NewLine)[0]^

^

    if (-not ($versionLine -match "version `"([0-9]+)(\.([0-9]+))?.*`"")) {^

        Write-Error "Java reported a version that does not match the expected format: $versionLine"^

    }^

^

    $major = $Matches.1^

^

    if ($Matches.Count -ge 3) {^

        $minor = $Matches.3^

    } else {^

        $minor = "0"^

    }^

^

    return "$major.$minor"^

}^

^

function combineArgumentsToString([Object[]]$arguments) {^

    $combined = @()^

^

    $arguments ^| %% { $combined += escapeArgument($_) }^

^

    return $combined -join " "^

}^

^

function escapeArgument([String]$argument) {^

    return '"' + $argument.Replace('"', '"""') + '"'^

}^

^

main @args^



if not exist "%cacheDir%" (
    mkdir "%cacheDir%"
)

echo !script! > "%ps1Path%"

set BATECT_WRAPPER_SCRIPT_DIR=%~dp0

rem Why do we explicitly exit?
rem cmd.exe appears to read this script one line at a time and then executes it.
rem If we modify the script while it is still running (eg. because we're updating it), then cmd.exe does all kinds of odd things
rem because it continues execution from the next byte (which was previously the end of the line).
rem By explicitly exiting on the same line as starting the application, we avoid these issues as cmd.exe has already read the entire
rem line before we start the application and therefore will always exit.

rem Why do we set PSModulePath?
rem See issue #627
set "PSModulePath="
powershell.exe -ExecutionPolicy Bypass -NoLogo -NoProfile -File "%ps1Path%" %* && exit /b 0 || exit /b !ERRORLEVEL!

rem What's this for?
rem This is so the tests for the wrapper has a way to ensure that the line above terminates the script correctly.
echo WARNING: you should never see this, and if you do, then Batect's wrapper script has a bug
