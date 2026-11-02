# Exercises Windows Installer with DEBUG files under a new identity. Does NOT launch the app.
# Production cleanup is disabled in the fixture; cleanup logic is verified separately on fixtures.
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
$image=Join-Path $project 'desktopApp\build\debug\compose\binaries\main\exe'
$info=Get-Content -LiteralPath (Join-Path $image 'installer-definition.json') -Raw|ConvertFrom-Json
if(-not $info.debug -or $info.name -ne 'SquiTVDebug'){throw 'DEBUG resources required'}
$id=[Guid]::NewGuid().ToString('N').Substring(0,8)
$root=[IO.Path]::GetFullPath((Join-Path $project ".artifacts\msi-lifecycle\$id"))
$workspace=[IO.Path]::GetFullPath($project).TrimEnd('\')+'\'
if(-not $root.StartsWith($workspace,[StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $root)){throw 'Invalid fixture root'}
New-Item -ItemType Directory -Path $root|Out-Null
. (Join-Path $PSScriptRoot 'windows-installer\prepare-msi.ps1')
$wi=New-Object -ComObject WindowsInstaller.Installer
$upgrade='{'+[Guid]::NewGuid().ToString().ToUpperInvariant()+'}'
$componentMap=@{}
$codes=@(1..2 | ForEach-Object { '{'+[Guid]::NewGuid().ToString().ToUpperInvariant()+'}' })
function New-FixtureMsi([string]$Path,[string]$Code,[string]$Version){
 Copy-Item -LiteralPath (Join-Path $image 'payloads\application.msi') -Destination $Path
 $db=$wi.OpenDatabase($Path,1)
 try {
  $db.ApplyTransform((Join-Path $image 'payloads\legacy-user.mst'),0)|Out-Null
  Set-MsiProperty $db 'ProductCode' $Code;Set-MsiProperty $db 'UpgradeCode' $upgrade
  Set-MsiProperty $db 'ProductVersion' $Version;Set-MsiProperty $db 'ProductName' "Squi TV Debug Installer Check $id"
  $view=$db.OpenView('SELECT `VersionMin`,`VersionMax`,`Language`,`Attributes`,`Remove`,`ActionProperty` FROM `Upgrade`');$view.Execute()|Out-Null
  $upgrades=@()
  function Sql-Literal([string]$Value){if($Value -eq ''){return 'NULL'};return "'"+$Value.Replace("'","''")+"'"}
  while($row=$view.Fetch()){
   $values=@((Sql-Literal $upgrade),(Sql-Literal $row.StringData(1)),(Sql-Literal $row.StringData(2)),
     (Sql-Literal $row.StringData(3)),([string]$row.IntegerData(4)),(Sql-Literal $row.StringData(5)),(Sql-Literal $row.StringData(6)))
   $upgrades+=('INSERT INTO `Upgrade` (`UpgradeCode`,`VersionMin`,`VersionMax`,`Language`,`Attributes`,`Remove`,`ActionProperty`) VALUES ('+($values -join ',')+')')
  }
  $view.Close()|Out-Null;[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null
  Invoke-MsiSql $db 'DELETE FROM `Upgrade`';foreach($sql in $upgrades){Invoke-MsiSql $db $sql}
  Invoke-MsiSql $db 'UPDATE `InstallExecuteSequence` SET `Condition`=''0'' WHERE `Action`=''SquiTVCleanup'''
  Invoke-MsiSql $db 'DELETE FROM `Shortcut`'
  Invoke-MsiSql $db ('UPDATE `Registry` SET `Key`=''Software\SquiTVInstallerChecks\'+$id+'''')
  $view=$db.OpenView('SELECT `Component`,`ComponentId` FROM `Component`');$view.Execute()|Out-Null
  $rows=@();while($row=$view.Fetch()){$rows+=,@($row.StringData(1),$row.StringData(2))}
  $view.Close()|Out-Null;[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null
  foreach($row in $rows){
   if(-not $row[1]){continue};if(-not $componentMap.ContainsKey($row[1])){$componentMap[$row[1]]='{'+[Guid]::NewGuid().ToString().ToUpperInvariant()+'}'}
   Invoke-MsiSql $db ('UPDATE `Component` SET `ComponentId`='''+$componentMap[$row[1]]+''' WHERE `Component`='''+$row[0]+'''')
  }
  # No shared Start menu/Desktop directory or registry key belongs to this isolated product.
  $view=$db.OpenView('SELECT `Directory`,`DefaultDir` FROM `Directory`');$view.Execute()|Out-Null
  $dirs=@();while($row=$view.Fetch()){if($row.StringData(2) -match 'Squi'){$dirs+=,$row.StringData(1)}}
  $view.Close()|Out-Null;[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null
  foreach($directory in $dirs){Invoke-MsiSql $db ('UPDATE `Directory` SET `DefaultDir`=''SquiTVFixture-'+$id+''' WHERE `Directory`='''+$directory+'''')}
  $summary=$db.SummaryInformation(1);Set-ComProperty $summary 'Property' @(9,('{'+[Guid]::NewGuid().ToString().ToUpperInvariant()+'}'));$summary.Persist()|Out-Null
  [Runtime.InteropServices.Marshal]::FinalReleaseComObject($summary)|Out-Null
  $db.Commit()|Out-Null
 } finally {[Runtime.InteropServices.Marshal]::FinalReleaseComObject($db)|Out-Null}
}
function Invoke-FixtureMsi([string]$Arguments,[string]$Log,[switch]$AllowAbsent){
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName=Join-Path ([Environment]::GetFolderPath('System')) 'msiexec.exe'
 $start.Arguments=$Arguments+' /qn /norestart REBOOT=ReallySuppress MSIRESTARTMANAGERCONTROL=Disable /L*v "'+$Log+'"'
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true
 $process=[Diagnostics.Process]::Start($start)
 if(-not $process.WaitForExit(120000)){throw 'Fixture MSI exceeded deadline; do not terminate Windows Installer service'}
 $result=$process.ExitCode;$process.Dispose()
 if($result -ne 0 -and $result -ne 3010 -and -not($AllowAbsent -and $result -eq 1605)){throw "Fixture MSI failed ($result); see $Log"}
}
$old=Join-Path $root 'old.msi';$new=Join-Path $root 'new.msi'
New-FixtureMsi $old $codes[0] '1.22.0';New-FixtureMsi $new $codes[1] $info.version
$app=Join-Path $root 'chosen-location'
try {
 Invoke-FixtureMsi ('/i "'+$old+'" INSTALLDIR="'+$app+'"') (Join-Path $root 'install.log')
 if(-not(Test-Path -LiteralPath (Join-Path $app 'SquiTVDebug.exe'))){throw 'Fixture executable not installed'}
 Invoke-FixtureMsi ('/i "'+$new+'" INSTALLDIR="'+$app+'"') (Join-Path $root 'upgrade.log')
 if($wi.ProductState($codes[0]) -ne -1){throw 'Previous fixture product remains registered'}
 if($wi.ProductInfo($codes[1],'InstallLocation').TrimEnd('\') -ne $app.TrimEnd('\')){throw 'Upgrade changed install location'}
 Invoke-FixtureMsi ('/x '+$codes[1]) (Join-Path $root 'uninstall.log')
 if(Test-Path -LiteralPath (Join-Path $app 'SquiTVDebug.exe')){throw 'Fixture executable remained after uninstall'}
 Write-Output 'PASS: real Windows Installer install/upgrade/uninstall of isolated DEBUG fixture; chosen directory preserved; no application launched'
} finally {
 foreach($code in $codes){Invoke-FixtureMsi ('/x '+$code) (Join-Path $root (($code.Trim('{}'))+'-cleanup.log')) -AllowAbsent}
 $key='HKCU:\Software\SquiTVInstallerChecks\'+$id
 if(Test-Path -LiteralPath $key){Remove-Item -LiteralPath $key -Recurse -Force}
}
