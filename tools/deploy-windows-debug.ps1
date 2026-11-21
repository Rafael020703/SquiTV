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
function Get-PhysicalFilePath([string]$Path, [IO.FileStream]$OpenStream = $null) {
    $stream = $OpenStream
    if (-not $stream) {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    }
    try {
        $buffer = New-Object Text.StringBuilder 32768
        $length = [SquiTVDeployment.NativePaths]::GetFinalPathNameByHandle($stream.SafeFileHandle, $buffer, $buffer.Capacity, 0)
        if ($length -eq 0 -or $length -ge $buffer.Capacity) { throw 'Nao foi possivel resolver o caminho fisico do atalho.' }
        $physical = $buffer.ToString()
        if ($physical.StartsWith('\\?\UNC\')) { return '\\' + $physical.Substring(8) }
        if ($physical.StartsWith('\\?\')) { return $physical.Substring(4) }
        return $physical
    } finally { if (-not $OpenStream) { $stream.Dispose() } }
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
$logicalPortableHome = $portableHome
# Reuse the physical installation even when the next deploy runs outside this host.
$programsHome = [Environment]::GetFolderPath('Programs')
$shortcutDirectory = Join-Path $programsHome 'Squi TV Debug'
$shortcutFile = Join-Path $shortcutDirectory 'SquiTVDebug.lnk'
$shell = New-Object -ComObject WScript.Shell
if (Test-Path -LiteralPath $shortcutFile -PathType Leaf) {
    $existingTarget = $shell.CreateShortcut($shortcutFile).TargetPath
    $localBoundary = [IO.Path]::GetFullPath($env:LOCALAPPDATA).TrimEnd('\') + '\'
    if ($existingTarget.StartsWith($localBoundary, [StringComparison]::OrdinalIgnoreCase) -and
        $existingTarget -match '\\SquiTVDebugPortable\\(?:current|builds\\[a-f0-9]{16})\\SquiTVDebug\.exe$' -and
        (Test-Path -LiteralPath $existingTarget -PathType Leaf)) {
        $physicalParent = Split-Path -Parent (Get-PhysicalFilePath $existingTarget)
        $portableHome = Split-Path -Parent $physicalParent
        if ((Split-Path -Leaf $portableHome) -eq 'builds') { $portableHome = Split-Path -Parent $portableHome }
    } elseif ($existingTarget.StartsWith($localBoundary, [StringComparison]::OrdinalIgnoreCase) -and
        $existingTarget.EndsWith('\SquiTVDebugPortable\SquiTVDebugLauncher.exe', [StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $existingTarget -PathType Leaf)) {
        $portableHome = Split-Path -Parent (Get-PhysicalFilePath $existingTarget)
    }
}
New-Item -ItemType Directory -Path $portableHome -Force | Out-Null
# Serialize deploys and resolve the host's redirected directory before any cleanup.
$deployLock = [IO.File]::Open((Join-Path $portableHome '.deploy.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
try {
    $portableHome = Split-Path -Parent (Get-PhysicalFilePath -OpenStream $deployLock)
    # The desktop host can merge old real-LOCALAPPDATA entries with its redirected view.
    # Enumerate that view for legacy cleanup, but deploy the new image at a physical path.
    $portableRoots = @($portableHome, $logicalPortableHome) | Select-Object -Unique
    $buildsHome = Join-Path $portableHome 'builds'
    $debugBuild = Join-Path $buildsHome $buildHash.Substring(0, 16)
    $pendingBuild = Join-Path $portableHome 'pending'

    function Assert-PortableTree([string]$Path) {
        $absolute = [IO.Path]::GetFullPath($Path).TrimEnd('\')
        $matchedRoot = $null
        foreach ($root in $portableRoots) {
            if ($absolute.StartsWith($root.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
                $matchedRoot = $root
                break
            }
        }
        if (-not $matchedRoot) {
            throw "Operacao fora da pasta debug portatil: $absolute"
        }
        # Reject junctions/symlinks in ancestors and descendants, rather than follow them.
        $ancestor = $absolute
        while ($ancestor.Length -ge $matchedRoot.Length) {
            if ((Test-Path -LiteralPath $ancestor) -and
                ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw "Link inesperado na pasta debug: $ancestor"
            }
            $ancestor = Split-Path -Parent $ancestor
        }
        if (Test-Path -LiteralPath $absolute) {
            $directories = New-Object 'Collections.Generic.Stack[string]'
            $directories.Push($absolute)
            while ($directories.Count -gt 0) {
                foreach ($entry in Get-ChildItem -LiteralPath $directories.Pop() -Force) {
                    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                        throw "Link inesperado na pasta debug: $($entry.FullName)"
                    }
                    if ($entry.PSIsContainer) { $directories.Push($entry.FullName) }
                }
            }
        }
    }
    function Get-CompletedBuildHash([string]$Path) {
        $marker = Join-Path $Path '.copy-complete'
        if (-not (Test-Path -LiteralPath $marker -PathType Leaf)) { return $null }
        $value = ([IO.File]::ReadAllText($marker)).Trim()
        if ($value -notmatch '^[a-f0-9]{64}$') { return $null }
        foreach ($relativeFile in $requiredFiles) {
            if (-not (Test-Path -LiteralPath (Join-Path $Path $relativeFile) -PathType Leaf)) { return $null }
        }
        return $value
    }
    function Test-BuildInUse([string]$Path) {
        $prefix = [IO.Path]::GetFullPath($Path).TrimEnd('\') + '\'
        $executable = Join-Path $Path 'SquiTVDebug.exe'
        if (Test-Path -LiteralPath $executable -PathType Leaf) {
            $prefix = (Split-Path -Parent (Get-PhysicalFilePath $executable)).TrimEnd('\') + '\'
        }
        foreach ($process in Get-CimInstance Win32_Process -Filter "Name = 'SquiTVDebug.exe' OR Name = 'java.exe' OR Name = 'javaw.exe'") {
            if (-not $process.ExecutablePath) {
                # Without a path, a debug launcher cannot safely be assigned to another build.
                if ($process.Name -eq 'SquiTVDebug.exe') { return $true }
                continue
            }
            if ($process.ExecutablePath.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { return $true }
        }
        return $false
    }
    function Remove-PortableTree([string]$Path) {
        Assert-PortableTree $Path
        if (Test-BuildInUse $Path) { throw 'Feche o Squi TV Debug e execute o deploy novamente. Nenhum processo foi encerrado.' }
        Remove-Item -LiteralPath $Path -Recurse -Force
    }

    Assert-PortableTree $buildsHome
    New-Item -ItemType Directory -Path $buildsHome -Force | Out-Null
    Assert-PortableTree $debugBuild
    if (Test-Path -LiteralPath $debugBuild) {
        if ((Get-CompletedBuildHash $debugBuild) -ne $buildHash) {
            throw 'A pasta da build existente não corresponde ao pacote.'
        }
    } else {
        Assert-PortableTree $pendingBuild
        if (Test-Path -LiteralPath $pendingBuild) {
            $ownedMarker = Join-Path $pendingBuild '.deploy-owned'
            if (-not (Test-Path -LiteralPath $ownedMarker -PathType Leaf) -or
                ([IO.File]::ReadAllText($ownedMarker)).Trim() -ne 'SquiTVDebugPortable-v1') {
                throw 'Pasta temporária não reconhecida.'
            }
            Remove-PortableTree $pendingBuild
        }
        New-Item -ItemType Directory -Path $pendingBuild | Out-Null
        Set-Content -LiteralPath (Join-Path $pendingBuild '.deploy-owned') -Value 'SquiTVDebugPortable-v1' -Encoding UTF8
        foreach ($entry in Get-ChildItem -LiteralPath $debugSource -Force) {
            Copy-Item -LiteralPath $entry.FullName -Destination $pendingBuild -Recurse
        }
        Set-Content -LiteralPath (Join-Path $pendingBuild '.copy-complete') -Value $buildHash -Encoding UTF8
        Assert-PortableTree $pendingBuild
        Move-Item -LiteralPath $pendingBuild -Destination $debugBuild
    }

    # Existing SquiTVDebug DPAPI data stays in its original folder; no account files are copied.
    $debugExecutable = Join-Path $debugBuild 'SquiTVDebug.exe'
    $launcher = Join-Path $portableHome 'SquiTVDebugLauncher.exe'
    if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
        $compiler = Join-Path $env:SystemRoot 'Microsoft.NET\Framework64\v4.0.30319\csc.exe'
        $source = Join-Path $PSScriptRoot 'windows-debug-launcher.cs'
        $temporaryLauncher = Join-Path $portableHome ('launcher-' + [Guid]::NewGuid().ToString('N') + '.exe')
        try {
            & $compiler /nologo /target:winexe /reference:System.Windows.Forms.dll "/win32icon:$brandIcon" "/out:$temporaryLauncher" $source
            if ($LASTEXITCODE -ne 0) { throw 'Falha ao gerar o inicializador fixo da debug.' }
            Move-Item -LiteralPath $temporaryLauncher -Destination $launcher
        } finally {
            if (Test-Path -LiteralPath $temporaryLauncher) { Remove-Item -LiteralPath $temporaryLauncher }
        }
    }

    $resolvedLauncher = Get-PhysicalFilePath $launcher
    $activeBuild = Join-Path $portableHome 'active-build.txt'
    $pendingActivation = Join-Path $portableHome ('active-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    try {
        [IO.File]::WriteAllText($pendingActivation, $buildHash.Substring(0, 16))
        if (Test-Path -LiteralPath $activeBuild) {
            [IO.File]::Replace($pendingActivation, $activeBuild, [System.Management.Automation.Language.NullString]::Value)
        }
        else { Move-Item -LiteralPath $pendingActivation -Destination $activeBuild }
    } finally {
        if (Test-Path -LiteralPath $pendingActivation) { Remove-Item -LiteralPath $pendingActivation }
    }

    $shortcutIcon = "$resolvedLauncher,0"
    $existing = if (Test-Path -LiteralPath $shortcutFile) { $shell.CreateShortcut($shortcutFile) }
    if (-not $existing -or $existing.TargetPath -ne $resolvedLauncher) {
        New-Item -ItemType Directory -Path $shortcutDirectory -Force | Out-Null
        $pendingShortcut = Join-Path $shortcutDirectory ('debug-' + [Guid]::NewGuid().ToString('N') + '.lnk')
        try {
            $shortcut = $shell.CreateShortcut($pendingShortcut)
            $shortcut.TargetPath = $resolvedLauncher
            $shortcut.WorkingDirectory = $portableHome
            $shortcut.IconLocation = $shortcutIcon
            $shortcut.Description = 'Squi TV Debug - versao portatil de desenvolvimento'
            $shortcut.Save()
            Move-Item -LiteralPath $pendingShortcut -Destination $shortcutFile -Force
        } finally {
            if (Test-Path -LiteralPath $pendingShortcut) { Remove-Item -LiteralPath $pendingShortcut }
        }
    }
    Set-Content -LiteralPath (Join-Path $portableHome 'current-build.txt') -Value $debugBuild -Encoding UTF8

    # Migrate only recognized generated copies, never app data or an active old build.
    $removedBuilds = 0
    $retainedBuilds = 0
    foreach ($legacyName in @('current', 'previous')) {
        $legacy = Join-Path $portableHome $legacyName
        Assert-PortableTree $legacy
        if (Get-CompletedBuildHash $legacy) {
            try { Remove-PortableTree $legacy; $removedBuilds++ }
            catch { $retainedBuilds++ }
        }
    }
    if (Test-Path -LiteralPath $buildsHome) {
        Assert-PortableTree $buildsHome
        foreach ($oldBuild in Get-ChildItem -LiteralPath $buildsHome -Directory -Force) {
            if ($oldBuild.FullName -eq $debugBuild) { continue }
            Assert-PortableTree $oldBuild.FullName
            $oldHash = Get-CompletedBuildHash $oldBuild.FullName
            if ($oldBuild.Name -notmatch '^[a-f0-9]{16}(-[a-f0-9]{6})?$' -or -not $oldHash -or
                -not $oldBuild.Name.StartsWith($oldHash.Substring(0, 16)) -or (Test-BuildInUse $oldBuild.FullName)) {
                $retainedBuilds++
                continue
            }
            try { Remove-PortableTree $oldBuild.FullName; $removedBuilds++ }
            catch { $retainedBuilds++; Write-Warning "Copia antiga preservada: $($oldBuild.Name). $($_.Exception.Message)" }
        }
        if (@(Get-ChildItem -LiteralPath $buildsHome -Force).Count -eq 0) { Remove-PortableTree $buildsHome }
    }
    $backupDirectory = Join-Path $logicalPortableHome 'shortcut-backups'
    if (Test-Path -LiteralPath $backupDirectory) {
        Assert-PortableTree $backupDirectory
        foreach ($backup in Get-ChildItem -LiteralPath $backupDirectory -File -Force) {
            if ($backup.Name -match '^[a-f0-9]{32}\.lnk$') { Remove-Item -LiteralPath $backup.FullName }
        }
        if (@(Get-ChildItem -LiteralPath $backupDirectory -Force).Count -eq 0) { Remove-PortableTree $backupDirectory }
    }
    Write-Output "Debug portatil: $debugExecutable"
    Write-Output "Build: $($buildHash.Substring(0, 16)). Copias antigas removidas: $removedBuilds; preservadas/em uso: $retainedBuilds."
    Write-Output "Atalho no Iniciar: $shortcutFile"
    Write-Output "Icone do atalho: $shortcutIcon"
    Write-Output 'Dados preservados em %LOCALAPPDATA%\SquiTVDebug. Nenhum processo foi encerrado ou iniciado.'
} finally {
    $deployLock.Dispose()
}
