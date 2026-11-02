param(
    [string]$CredentialsFile,
    [switch]$FromStdin
)
$ErrorActionPreference = 'Stop'

# Credentials are read from a private local file or stdin, never from process arguments.
if ($FromStdin) {
    $sourceJson = [Console]::ReadLine()
} else {
    if (!$CredentialsFile) { throw 'Informe CredentialsFile ou FromStdin.' }
    $inputFile = (Resolve-Path -LiteralPath $CredentialsFile).Path
    if ((Get-Item -LiteralPath $inputFile).Length -gt 32768) { throw 'Arquivo de credenciais inválido.' }
    $sourceJson = Get-Content -LiteralPath $inputFile -Raw
}
if (!$sourceJson -or $sourceJson.Length -gt 32768) { throw 'Dados de credenciais inválidos.' }
try { $account = $sourceJson | ConvertFrom-Json }
catch { throw 'Informe um JSON local com baseUrl, username e password.' }
if ([string]::IsNullOrWhiteSpace($account.baseUrl) -or
    [string]::IsNullOrWhiteSpace($account.username) -or [string]::IsNullOrEmpty($account.password)) {
    throw 'Preencha baseUrl, username e password no arquivo local.'
}
$server = $null
if (![Uri]::TryCreate([string]$account.baseUrl, [UriKind]::Absolute, [ref]$server) -or
    $server.Scheme -notin @('http', 'https') -or $server.UserInfo -or $server.Query -or $server.Fragment) {
    throw 'O endereço deve ser HTTP/HTTPS, sem credenciais ou parâmetros na URL.'
}
$directory = Join-Path $env:LOCALAPPDATA 'SquiTVDebug'
[IO.Directory]::CreateDirectory($directory) | Out-Null
Add-Type -AssemblyName System.Security
$data = [ordered]@{
    baseUrl = $server.AbsoluteUri.TrimEnd('/') + '/'
    username = [string]$account.username
    password = [string]$account.password
} | ConvertTo-Json -Compress
$clearBytes = [Text.Encoding]::UTF8.GetBytes($data)
try {
    $encrypted = [Security.Cryptography.ProtectedData]::Protect(
        $clearBytes, $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    # Keep developer autofill separate from the optional remembered session. A login
    # without 'remember' may clear account.dpapi but must not erase debug defaults.
    $destinations = @((Join-Path $directory 'debug-login.dpapi'))
    if (!(Test-Path -LiteralPath (Join-Path $directory 'account.dpapi'))) {
        $destinations += (Join-Path $directory 'account.dpapi')
    }
    foreach ($destination in $destinations) {
        $temporary = Join-Path $directory ([IO.Path]::GetRandomFileName())
        try {
            [IO.File]::WriteAllBytes($temporary, $encrypted)
            Move-Item -LiteralPath $temporary -Destination $destination -Force
        } finally {
            if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
        }
    }
} finally { [Array]::Clear($clearBytes, 0, $clearBytes.Length) }
Write-Output 'Conta de teste configurada somente no Windows Debug. Reabra o aplicativo Debug.'
