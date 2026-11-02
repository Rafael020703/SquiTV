param(
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '..\desktopApp\build\debug\compose\binaries\main\app\SquiTVDebug'),
    [string]$IconFile = (Join-Path $PSScriptRoot '..\desktopApp\build\debug\generated\assets\brand\app-icon.ico')
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'A debug portatil requer Windows.' }
if (-not ('SquiTVDeployment.NativePaths' -as [type])) {
    Add-Type -TypeDefinition @'
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;
namespace SquiTVDeployment {
    public static class NativePaths {
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        public static extern uint GetFinalPathNameByHandle(SafeFileHandle handle, StringBuilder path, uint size, uint flags);
    }
}
'@
}
function Get-PhysicalFilePath([string]$Path) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $buffer = New-Object Text.StringBuilder 32768
        $length = [SquiTVDeployment.NativePaths]::GetFinalPathNameByHandle($stream.SafeFileHandle, $buffer, $buffer.Capacity, 0)
        if ($length -eq 0 -or $length -ge $buffer.Capacity) { throw 'Nao foi possivel resolver o caminho fisico do atalho.' }
        $physical = $buffer.ToString()
        if ($physical.StartsWith('\\?\UNC\')) { return '\\' + $physical.Substring(8) }
        if ($physical.StartsWith('\\?\')) { return $physical.Substring(4) }
        return $physical
    } finally { $stream.Dispose() }
}
$debugSource = (Resolve-Path -LiteralPath $SourceDirectory).Path
$brandIcon = (Resolve-Path -LiteralPath $IconFile).Path
$requiredFiles = @('SquiTVDebug.exe', 'app\SquiTVDebug.cfg', 'app\resources\vlc\libvlc.dll',
    'app\resources\vlc\SQUI-TV-RUNTIME.txt', 'runtime\release')
foreach ($relativeFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $debugSource $relativeFile) -PathType Leaf)) {
        throw "Imagem debug incompleta: $relativeFile. Execute :desktopApp:createDistributable -PdesktopVariant=debug."
    }
}

# Pin the local debug autofill's physical location, since an app started outside the
# desktop host can see a different LOCALAPPDATA view. Only a path is put in the launcher;
# encrypted credentials remain outside the portable image and are never distributed.
$defaultsFile = Join-Path $env:LOCALAPPDATA 'SquiTVDebug\debug-login.dpapi'
$launcherConfig = Join-Path $debugSource 'app\SquiTVDebug.cfg'
if (Test-Path -LiteralPath $defaultsFile -PathType Leaf) {
    $physicalDefaults = Get-PhysicalFilePath $defaultsFile
    $configLines = [IO.File]::ReadAllLines($launcherConfig)
    $updatedConfig = foreach ($line in $configLines) {
        if ($line.StartsWith('java-options=-Dsquitv.debug.defaultsFile=')) { continue }
        $line
        if ($line -eq '[JavaOptions]') { "java-options=-Dsquitv.debug.defaultsFile=$physicalDefaults" }
    }
    [IO.File]::WriteAllLines($launcherConfig, [string[]]$updatedConfig, (New-Object Text.UTF8Encoding $false))
}

# The launcher config contains content-based JAR names. Include native/runtime provenance too.
$hashParts = foreach ($relativeFile in $requiredFiles) {
    (Get-FileHash -LiteralPath (Join-Path $debugSource $relativeFile) -Algorithm SHA256).Hash
}
$hashInput = [Text.Encoding]::UTF8.GetBytes(($hashParts -join '|'))
$hashProvider = [Security.Cryptography.SHA256]::Create()
try { $buildHash = ([BitConverter]::ToString($hashProvider.ComputeHash($hashInput))).Replace('-', '').ToLowerInvariant() }
finally { $hashProvider.Dispose() }

$portableHome = Join-Path $env:LOCALAPPDATA 'SquiTVDebugPortable'
$buildsHome = Join-Path $portableHome 'builds'
New-Item -ItemType Directory -Path $buildsHome -Force | Out-Null
$debugBuild = Join-Path $buildsHome $buildHash.Substring(0, 16)
$completionFile = Join-Path $debugBuild '.copy-complete'
if (-not (Test-Path -LiteralPath $completionFile)) {
    if (Test-Path -LiteralPath $debugBuild) {
        # Preserve an incomplete prior copy instead of deleting potentially active/user files.
        $debugBuild += '-' + [Guid]::NewGuid().ToString('N').Substring(0, 6)
        $completionFile = Join-Path $debugBuild '.copy-complete'
    }
    Copy-Item -LiteralPath $debugSource -Destination $debugBuild -Recurse
    Set-Content -LiteralPath $completionFile -Value $buildHash -Encoding UTF8
}

# Existing SquiTVDebug DPAPI data stays in its original folder; no account files are copied.
$debugExecutable = Join-Path $debugBuild 'SquiTVDebug.exe'
$programsHome = [Environment]::GetFolderPath('Programs')
$shortcutDirectory = Join-Path $programsHome 'Squi TV Debug'
New-Item -ItemType Directory -Path $shortcutDirectory -Force | Out-Null
$shortcutFile = Join-Path $shortcutDirectory 'SquiTVDebug.lnk'
$shell = New-Object -ComObject WScript.Shell
if (Test-Path -LiteralPath $shortcutFile) {
    $oldTarget = $shell.CreateShortcut($shortcutFile).TargetPath
    if ($oldTarget -ne $debugExecutable) {
        $backupDirectory = Join-Path $portableHome 'shortcut-backups'
        New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
        Copy-Item -LiteralPath $shortcutFile -Destination (Join-Path $backupDirectory (([Guid]::NewGuid().ToString('N')) + '.lnk'))
    }
}
$pendingShortcut = Join-Path $shortcutDirectory ('debug-' + [Guid]::NewGuid().ToString('N') + '.lnk')
try {
    $shortcut = $shell.CreateShortcut($pendingShortcut)
    $shortcut.TargetPath = $debugExecutable
    $shortcut.WorkingDirectory = $debugBuild
    $shortcut.IconLocation = "$debugExecutable,0"
    $shortcut.Description = 'Squi TV Debug - versao portatil de desenvolvimento'
    $shortcut.Save()
    # The desktop host may redirect LOCALAPPDATA. WScript resolves TargetPath when saving,
    # but leaves IconLocation verbatim. Use the resolved location for the icon as well.
    $shortcut = $shell.CreateShortcut($pendingShortcut)
    $resolvedExecutable = Get-PhysicalFilePath $debugExecutable
    $shortcut.TargetPath = $resolvedExecutable
    $shortcut.WorkingDirectory = Split-Path -Parent $resolvedExecutable
    $actualPortableHome = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $resolvedExecutable))
    $actualLocalData = Split-Path -Parent $actualPortableHome
    $iconDirectory = Join-Path $actualLocalData 'SquiTVBrand\icons'
    New-Item -ItemType Directory -Path $iconDirectory -Force | Out-Null
    $iconHash = (Get-FileHash -LiteralPath $brandIcon -Algorithm SHA256).Hash.ToLowerInvariant()
    $shortcutIcon = Join-Path $iconDirectory ($iconHash + '.ico')
    if (-not (Test-Path -LiteralPath $shortcutIcon)) { Copy-Item -LiteralPath $brandIcon -Destination $shortcutIcon }
    $shortcutIcon = Get-PhysicalFilePath $shortcutIcon
    $shortcut.IconLocation = "$shortcutIcon,0"
    $shortcut.Save()
    Move-Item -LiteralPath $pendingShortcut -Destination $shortcutFile -Force
} finally {
    if (Test-Path -LiteralPath $pendingShortcut) { Remove-Item -LiteralPath $pendingShortcut }
}
Set-Content -LiteralPath (Join-Path $portableHome 'current-build.txt') -Value $debugBuild -Encoding UTF8
Write-Output "Debug portatil: $debugExecutable"
Write-Output "Atalho no Iniciar: $shortcutFile"
Write-Output "Icone do atalho: $shortcutIcon"
Write-Output 'Dados preservados em %LOCALAPPDATA%\SquiTVDebug. Nenhum processo foi encerrado ou iniciado.'
