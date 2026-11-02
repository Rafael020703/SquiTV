param([switch]$Network)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$image=Join-Path $projectRoot 'desktopApp\build\debug\compose\binaries\main\exe'
$metadata=Join-Path $image 'installer-definition.json'
$app=Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json
if (-not $app.debug -or $app.name -ne 'SquiTVDebug') { throw 'A verificacao exige recursos DEBUG.' }
$output=Join-Path $projectRoot ('.artifacts\online-installer-checks\' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $output -Force | Out-Null
$compiler=Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
$framework=Split-Path -Parent $compiler
$exe=Join-Path $output 'InstallerChecks.exe'
$msi=Join-Path $image 'payloads\application.msi'
$mst=Join-Path $image 'payloads\legacy-user.mst'
$manifest=Join-Path $projectRoot 'build\desktopNativeResources\vlc-3.0.23\windows\vlc\SQUI-TV-RUNTIME.json'
$argsCompiler=@('/nologo','/codepage:65001','/target:exe','/platform:x64','/main:SquiTV.Setup.InstallerChecks',"/out:`"$exe`"",
    ('/win32manifest:"' + (Join-Path $image 'installer.manifest') + '"'),
    "/resource:`"$msi`",squitv.application.msi", "/resource:`"$manifest`",squitv.runtime.json",
    "/resource:`"$metadata`",squitv.installer.json",
    "/resource:`"$mst`",squitv.legacy-user.mst",
    "`"$(Join-Path $PSScriptRoot 'windows-installer\Bootstrapper.cs')`"",
    ('"' + (Join-Path $PSScriptRoot 'windows-installer\InstallerChecks.cs') + '"')
)
foreach($dll in @('System.dll','System.Core.dll','System.Windows.Forms.dll','System.Drawing.dll',
 'System.Net.Http.dll','System.Web.Extensions.dll','System.IO.Compression.dll','System.IO.Compression.FileSystem.dll')) {
 $argsCompiler+="/reference:`"$(Join-Path $framework $dll)`""
}
$response=Join-Path $output 'checks.rsp'
[IO.File]::WriteAllLines($response,$argsCompiler,(New-Object Text.UTF8Encoding $true))
& $compiler "@$response"
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar verificacoes debug.'}
$archive=Join-Path $projectRoot '.artifacts\native\vlc-3.0.23-win64.zip'
$arguments=@($archive,(Join-Path $output 'isolated'))
if($Network){$arguments+='--network'}
& $exe @arguments
if($LASTEXITCODE -ne 0){throw 'Falha nas verificacoes do instalador debug.'}

# Inspect the actual MSI and its legacy transform, without opening an install session.
$installer=New-Object -ComObject WindowsInstaller.Installer
function Read-Scalar($Database,[string]$Sql){$view=$Database.OpenView($Sql);try{$view.Execute()|Out-Null;$record=$view.Fetch();if($record){return $record.StringData(1)}}finally{$view.Close()|Out-Null;[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null}}
$db=$installer.OpenDatabase($msi,0)
try {
 if((Read-Scalar $db 'SELECT `Value` FROM `Property` WHERE `Property`=''ALLUSERS''') -ne '1'){throw 'Fresh install must be per-machine'}
 if((Read-Scalar $db 'SELECT `Directory_Parent` FROM `Directory` WHERE `Directory`=''INSTALLDIR''') -ne 'ProgramFiles64Folder'){throw 'Fresh install must use Program Files'}
 $condition=Read-Scalar $db 'SELECT `Condition` FROM `InstallExecuteSequence` WHERE `Action`=''SquiTVCleanup'''
 if($condition -ne 'REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE'){throw 'Cleanup is not protected during upgrades'}
 if((Read-Scalar $db 'SELECT `Target` FROM `CustomAction` WHERE `Action`=''SquiTVInstallLocation''') -ne '[INSTALLDIR]'){throw 'InstallLocation persistence missing'}
} finally {[Runtime.InteropServices.Marshal]::FinalReleaseComObject($db)|Out-Null}
$copy=Join-Path $output 'legacy-inspection.msi';Copy-Item -LiteralPath $msi -Destination $copy
$db=$installer.OpenDatabase($copy,1)
try {
 $db.ApplyTransform($mst,0);$db.Commit()
 if((Read-Scalar $db 'SELECT `Value` FROM `Property` WHERE `Property`=''ALLUSERS''')){throw 'Legacy scope not per-user'}
 if((Read-Scalar $db 'SELECT `Directory_Parent` FROM `Directory` WHERE `Directory`=''INSTALLDIR''') -ne 'LocalAppDataFolder'){throw 'Legacy directory invalid'}
 if((Read-Scalar $db 'SELECT `Type` FROM `CustomAction` WHERE `Action`=''SquiTVCleanup''') -ne '1026'){throw 'Legacy cleanup must impersonate current user'}
} finally {[Runtime.InteropServices.Marshal]::FinalReleaseComObject($db)|Out-Null}
Write-Output 'PASS: Program Files default, registered update location, cleanup upgrade guard, legacy per-user MST; MSI not executed'

$cleanupExe=Join-Path $output 'MaintenanceChecks.exe'
$cleanupArgs=@('/nologo','/codepage:65001','/target:exe','/platform:x64','/main:SquiTV.Setup.MaintenanceChecks',('/out:"'+$cleanupExe+'"'),
 ('/win32manifest:"'+(Join-Path $image 'installer.manifest')+'"'),
 ('"'+(Join-Path $PSScriptRoot 'windows-installer\Maintenance.cs')+'"'),
 ('"'+(Join-Path $PSScriptRoot 'windows-installer\MaintenanceChecks.cs')+'"'))
$cleanupResponse=Join-Path $output 'cleanup.rsp';[IO.File]::WriteAllLines($cleanupResponse,$cleanupArgs,(New-Object Text.UTF8Encoding $true))
& $compiler "@$cleanupResponse"
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar verificacao de limpeza'}
& $cleanupExe (Join-Path $output 'cleanup-fixture')
if($LASTEXITCODE -ne 0){throw 'Falha na verificacao de limpeza isolada'}
