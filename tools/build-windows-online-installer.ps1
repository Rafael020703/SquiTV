param(
    [Parameter(Mandatory)][string]$Msi,
    [Parameter(Mandatory)][string]$RuntimeManifest,
    [Parameter(Mandatory)][string]$Source,
    [Parameter(Mandatory)][string]$Icon,
    [Parameter(Mandatory)][string]$Output,
    [Parameter(Mandatory)][ValidateSet('debug','release')][string]$Variant,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version
)
$ErrorActionPreference = 'Stop'
$compiler = Join-Path $env:WINDIR 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'O build do instalador requer o compilador .NET Framework 4.x do Windows x64.' }
$name = if ($Variant -eq 'debug') { 'SquiTVDebug' } else { 'SquiTV' }
if ((Split-Path -Leaf $Msi) -ne "$name-$Version.msi") { throw 'Identidade MSI incorreta.' }
if ((Split-Path -Leaf $Output) -ne "$name-$Version.exe") { throw 'Identidade EXE incorreta.' }
foreach ($file in @($Msi,$RuntimeManifest,$Source,$Icon)) {
    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Entrada de build ausente: $file" }
}
$buildDirectory = Split-Path -Parent $Output
New-Item -ItemType Directory -Path $buildDirectory -Force | Out-Null
$payloads=Join-Path $buildDirectory 'payloads'
New-Item -ItemType Directory -Path $payloads -Force | Out-Null
$helper=Join-Path $payloads 'Maintenance.exe'
& $compiler /nologo /codepage:65001 /target:winexe /platform:x64 /optimize+ "/out:$helper" (Join-Path $PSScriptRoot 'windows-installer\Maintenance.cs')
if($LASTEXITCODE -ne 0){throw 'Falha ao compilar manutencao do instalador.'}
. (Join-Path $PSScriptRoot 'windows-installer\prepare-msi.ps1')
$payloadMsi=Join-Path $payloads 'application.msi'
$transform=Join-Path $payloads 'legacy-user.mst'
Prepare-MsiPayload -Original $Msi -Output $payloadMsi -Transform $transform -Maintenance $helper -Name $name
$metadata = Join-Path $buildDirectory 'installer-definition.json'
$sha = [Security.Cryptography.SHA256]::Create()
$stream = [IO.File]::OpenRead($payloadMsi)
try { $msiHash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
finally { $stream.Dispose(); $sha.Dispose() }
$sha=[Security.Cryptography.SHA256]::Create();$stream=[IO.File]::OpenRead($transform)
try{$mstHash=[BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLowerInvariant()}
finally{$stream.Dispose();$sha.Dispose()}
@{ version=$Version; name=$name; debug=($Variant -eq 'debug');
   upgradeUuid=$(if ($Variant -eq 'debug') { 'b473bd19-d856-4902-8aba-8a281b202ac8' } else { 'cf4fe9a9-552b-43ed-8ca3-9e226f623362' });
   msiSize=(Get-Item -LiteralPath $payloadMsi).Length; msiSha256=$msiHash;
   mstSize=(Get-Item -LiteralPath $transform).Length; mstSha256=$mstHash } |
    ConvertTo-Json | Set-Content -LiteralPath $metadata -Encoding UTF8
$manifest = Join-Path $buildDirectory 'installer.manifest'
@'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0">
 <trustInfo xmlns="urn:schemas-microsoft-com:asm.v3"><security><requestedPrivileges><requestedExecutionLevel level="asInvoker" uiAccess="false"/></requestedPrivileges></security></trustInfo>
 <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1"><application><supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}"/></application></compatibility>
 <application xmlns="urn:schemas-microsoft-com:asm.v3"><windowsSettings><longPathAware xmlns="http://schemas.microsoft.com/SMI/2016/WindowsSettings">true</longPathAware></windowsSettings></application>
</assembly>
'@ | Set-Content -LiteralPath $manifest -Encoding UTF8
$assembly = Join-Path $buildDirectory 'InstallerAssembly.cs'
@"
using System.Reflection;
[assembly: AssemblyTitle("$name Installer")]
[assembly: AssemblyProduct("$name Installer")]
[assembly: AssemblyCompany("Squi TV")]
[assembly: AssemblyVersion("$Version.0")]
[assembly: AssemblyFileVersion("$Version.0")]
[assembly: AssemblyInformationalVersion("$Version")]
"@ | Set-Content -LiteralPath $assembly -Encoding UTF8
$framework = Split-Path -Parent $compiler
$response = @('/nologo','/codepage:65001','/target:winexe','/platform:x64','/optimize+','/debug-',
    "/out:`"$Output`"", "/win32icon:`"$Icon`"", "/win32manifest:`"$manifest`"",
    "/resource:`"$payloadMsi`",squitv.application.msi", "/resource:`"$RuntimeManifest`",squitv.runtime.json",
    "/resource:`"$transform`",squitv.legacy-user.mst",
    "/resource:`"$metadata`",squitv.installer.json", "`"$Source`"", "`"$assembly`"")
foreach ($dll in @('System.dll','System.Core.dll','System.Windows.Forms.dll','System.Drawing.dll',
    'System.Net.Http.dll','System.Web.Extensions.dll','System.IO.Compression.dll','System.IO.Compression.FileSystem.dll')) {
    $response += "/reference:`"$(Join-Path $framework $dll)`""
}
$responseFile = Join-Path $buildDirectory 'compile-installer.rsp'
[IO.File]::WriteAllLines($responseFile,$response,(New-Object Text.UTF8Encoding $true))
& $compiler "@$responseFile"
if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar instalador: $LASTEXITCODE" }
Write-Output "Instalador online compilado: $Output"
