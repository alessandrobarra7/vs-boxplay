$ErrorActionPreference = "Stop"

$ProductName = "BOXPLAY"
$Version = "0.1.0"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path (Join-Path $ScriptRoot "..")
$ElectronDist = Resolve-Path (Join-Path $ProjectRoot "node_modules\electron\dist")
$ReleaseRoot = Join-Path $ProjectRoot "release"
$DistRoot = Join-Path $ProjectRoot "dist"
$PortableDir = Join-Path $ReleaseRoot "BOXPLAY-win-x64"
$AppDir = Join-Path $PortableDir "resources\app"
$PayloadDir = Join-Path $ReleaseRoot "installer-payload"
$ZipPath = Join-Path $PayloadDir "BOXPLAY-win-x64.zip"
$InstallerPath = Join-Path $DistRoot "BOXPLAY-Setup-$Version.exe"
$SedPath = Join-Path $ReleaseRoot "boxplay-setup.sed"

function Assert-ChildPath($ParentPath, $ChildPath) {
    $parent = [System.IO.Path]::GetFullPath([string]$ParentPath).TrimEnd("\") + "\"
    $child = [System.IO.Path]::GetFullPath([string]$ChildPath)
    if (-not $child.StartsWith($parent, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe path outside project: $child"
    }
}

foreach ($pathToClean in @($ReleaseRoot, $DistRoot)) {
    if (Test-Path -LiteralPath $pathToClean) {
        $resolvedPath = Resolve-Path $pathToClean
        Assert-ChildPath $ProjectRoot $resolvedPath
        Remove-Item -LiteralPath $resolvedPath -Recurse -Force
    }
}

New-Item -ItemType Directory -Force $PortableDir, $AppDir, $PayloadDir, $DistRoot | Out-Null

Copy-Item -Path (Join-Path $ElectronDist "*") -Destination $PortableDir -Recurse -Force
Rename-Item -LiteralPath (Join-Path $PortableDir "electron.exe") -NewName "BOXPLAY.exe"
Get-ChildItem -LiteralPath $PortableDir -Recurse -Force | ForEach-Object { $_.LastWriteTime = Get-Date }

Copy-Item -Path (Join-Path $ProjectRoot "src") -Destination $AppDir -Recurse -Force
Set-Content -Path (Join-Path $AppDir "package.json") -Value @"
{
  "name": "boxplay-windows-app",
  "version": "$Version",
  "productName": "BOXPLAY",
  "main": "src/main.js",
  "private": true
}
"@ -Encoding UTF8

Compress-Archive -Path (Join-Path $PortableDir "*") -DestinationPath $ZipPath -Force

Set-Content -Path (Join-Path $PayloadDir "install-boxplay.ps1") -Value @"
`$ErrorActionPreference = "Stop"

`$InstallDir = Join-Path `$env:LOCALAPPDATA "Programs\BOXPLAY"
`$PayloadZip = Join-Path `$PSScriptRoot "BOXPLAY-win-x64.zip"

if (Test-Path -LiteralPath `$InstallDir) {
    Remove-Item -LiteralPath `$InstallDir -Recurse -Force
}

New-Item -ItemType Directory -Force `$InstallDir | Out-Null
Expand-Archive -Path `$PayloadZip -DestinationPath `$InstallDir -Force

`$ExePath = Join-Path `$InstallDir "BOXPLAY.exe"
`$Shell = New-Object -ComObject WScript.Shell

`$DesktopPath = [Environment]::GetFolderPath([Environment+SpecialFolder]::DesktopDirectory)
`$DesktopShortcut = `$Shell.CreateShortcut((Join-Path `$DesktopPath "BOXPLAY.lnk"))
`$DesktopShortcut.TargetPath = `$ExePath
`$DesktopShortcut.WorkingDirectory = `$InstallDir
`$DesktopShortcut.IconLocation = `$ExePath
`$DesktopShortcut.Save()

`$ProgramsPath = [Environment]::GetFolderPath([Environment+SpecialFolder]::Programs)
`$StartMenuFolder = Join-Path `$ProgramsPath "BOXPLAY"
New-Item -ItemType Directory -Force `$StartMenuFolder | Out-Null
`$StartMenuShortcut = `$Shell.CreateShortcut((Join-Path `$StartMenuFolder "BOXPLAY.lnk"))
`$StartMenuShortcut.TargetPath = `$ExePath
`$StartMenuShortcut.WorkingDirectory = `$InstallDir
`$StartMenuShortcut.IconLocation = `$ExePath
`$StartMenuShortcut.Save()

Start-Process -FilePath `$ExePath
"@ -Encoding UTF8

Set-Content -Path (Join-Path $PayloadDir "install-boxplay.cmd") -Value @"
@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-boxplay.ps1"
"@ -Encoding ASCII

$EscapedInstallerPath = $InstallerPath
$EscapedPayloadDir = $PayloadDir.TrimEnd("\") + "\"
Set-Content -Path $SedPath -Value @"
[Version]
Class=IEXPRESS
SEDVersion=3
[Options]
PackagePurpose=InstallApp
ShowInstallProgramWindow=0
HideExtractAnimation=1
UseLongFileName=1
InsideCompressed=0
CAB_FixedSize=0
CAB_ResvCodeSigning=0
RebootMode=N
InstallPrompt=
DisplayLicense=
FinishMessage=$ProductName foi instalado.
TargetName=$EscapedInstallerPath
FriendlyName=$ProductName Setup
AppLaunched=install-boxplay.cmd
PostInstallCmd=<None>
AdminQuietInstCmd=
UserQuietInstCmd=
SourceFiles=SourceFiles
[SourceFiles]
SourceFiles0=$EscapedPayloadDir
[SourceFiles0]
BOXPLAY-win-x64.zip=
install-boxplay.ps1=
install-boxplay.cmd=
"@ -Encoding ASCII

$IExpressPath = Join-Path $env:WINDIR "System32\iexpress.exe"
if (-not (Test-Path -LiteralPath $IExpressPath)) {
    throw "iexpress.exe nao encontrado neste Windows."
}

$iexpressProcess = Start-Process -FilePath $IExpressPath -ArgumentList @("/N", $SedPath) -Wait -PassThru
if ($null -ne $iexpressProcess.ExitCode -and $iexpressProcess.ExitCode -ne 0) {
    throw "IExpress falhou com codigo $($iexpressProcess.ExitCode)."
}

$deadline = (Get-Date).AddMinutes(5)
while (-not (Test-Path -LiteralPath $InstallerPath) -and (Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 1
}

if (-not (Test-Path -LiteralPath $InstallerPath)) {
    throw "Instalador nao foi gerado em $InstallerPath."
}

Write-Output "Portable app: $PortableDir"
Write-Output "Installer: $InstallerPath"
