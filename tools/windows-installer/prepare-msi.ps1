# Build-time MSI authoring only. Never executes MSI or edits installed-product registration.
function Set-ComProperty($Object,[string]$Name,[object[]]$Arguments) {
    $Object.GetType().InvokeMember($Name,[Reflection.BindingFlags]::SetProperty,$null,$Object,$Arguments) | Out-Null
}
function Invoke-MsiSql($Database,[string]$Sql) {
    $view=$Database.OpenView($Sql)
    try {$view.Execute()|Out-Null} catch {throw "MSI authoring SQL failed: $Sql"}
    finally {$view.Close()|Out-Null;[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null}
}
function Set-MsiProperty($Database,[string]$Name,[string]$Value) {
    if($Name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$' -or $Value.Contains("'")){throw 'Invalid MSI property literal'}
    Invoke-MsiSql $Database ('DELETE FROM `Property` WHERE `Property`='''+$Name+'''')
    if($Value -ne ''){Invoke-MsiSql $Database ('INSERT INTO `Property` (`Property`,`Value`) VALUES ('''+$Name+''','''+$Value+''')')}
}
function Add-MsiAction($Database,[string]$Name,[int]$Type,[string]$Source,[string]$Target,[string]$Condition,[int]$Sequence) {
    Invoke-MsiSql $Database ('INSERT INTO `CustomAction` (`Action`,`Type`,`Source`,`Target`) VALUES ('''+$Name+''','+$Type+','''+$Source+''','''+$Target+''')')
    Invoke-MsiSql $Database ('INSERT INTO `InstallExecuteSequence` (`Action`,`Condition`,`Sequence`) VALUES ('''+$Name+''','''+$Condition+''','+$Sequence+')')
}
function Prepare-MsiPayload([string]$Original,[string]$Output,[string]$Transform,[string]$Maintenance,[string]$Name) {
    Copy-Item -LiteralPath $Original -Destination $Output -Force
    $installer=New-Object -ComObject WindowsInstaller.Installer
    $db=$installer.OpenDatabase($Output,1)
    try {
        # Directory picker uses Program Files for a fresh machine install. During an update the
        # bootstrapper supplies the registered INSTALLDIR and /passive, so location cannot drift.
        Set-MsiProperty $db 'ALLUSERS' '1'
        Set-MsiProperty $db 'ARPNOMODIFY' '1'
        Add-MsiAction $db 'SquiTVInstallLocation' 51 'ARPINSTALLLOCATION' '[INSTALLDIR]' '1' 1050
        $condition='REMOVE="ALL" AND NOT UPGRADINGPRODUCTCODE'
        $record=$installer.CreateRecord(2)
        Set-ComProperty $record 'StringData' @(1,'SquiTVCleanup')
        $record.SetStream(2,$Maintenance)
        $view=$db.OpenView('INSERT INTO `Binary` (`Name`,`Data`) VALUES (?,?)')
        try {$view.Execute($record)} finally {
            $view.Close();[Runtime.InteropServices.Marshal]::FinalReleaseComObject($view)|Out-Null
            [Runtime.InteropServices.Marshal]::FinalReleaseComObject($record)|Out-Null
        }
        Add-MsiAction $db 'SquiTVCleanup' 3074 'SquiTVCleanup' ('machine '+$Name) $condition 3490
        $db.Commit()
    } finally {[Runtime.InteropServices.Marshal]::FinalReleaseComObject($db)|Out-Null}
    # Small, authenticated transform for legacy per-user upgrades; one shared MSI payload.
    $userFile=$Output+'.user.msi'
    Copy-Item -LiteralPath $Output -Destination $userFile -Force
    $user=$installer.OpenDatabase($userFile,1)
    try {
        Set-MsiProperty $user 'ALLUSERS' ''
        Invoke-MsiSql $user 'UPDATE `Registry` SET `Root`=1 WHERE `Root`=2'
        Invoke-MsiSql $user 'INSERT INTO `Directory` (`Directory`,`Directory_Parent`,`DefaultDir`) VALUES (''LocalAppDataFolder'',''TARGETDIR'',''.'')'
        Invoke-MsiSql $user 'UPDATE `Directory` SET `Directory_Parent`=''LocalAppDataFolder'' WHERE `Directory`=''INSTALLDIR'''
        Invoke-MsiSql $user ('UPDATE `CustomAction` SET `Type`=1026, `Target`=''user '+$Name+''' WHERE `Action`=''SquiTVCleanup''')
        $summary=$user.SummaryInformation(1)
        Set-ComProperty $summary 'Property' @(15,([int]$summary.Property(15) -bor 8))
        $summary.Persist()
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($summary)|Out-Null
        $user.Commit()
    } finally {[Runtime.InteropServices.Marshal]::FinalReleaseComObject($user)|Out-Null}
    $base=$installer.OpenDatabase($Output,0);$user=$installer.OpenDatabase($userFile,0)
    try {
        if(Test-Path -LiteralPath $Transform){Remove-Item -LiteralPath $Transform}
        $user.GenerateTransform($base,$Transform)|Out-Null
        # Explicitly pin base MSI/transform bytes in the EXE; Windows also caches the applied MST.
        $user.CreateTransformSummaryInfo($base,$Transform,0,0)
    } finally {
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($base)|Out-Null
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($user)|Out-Null
    }
}
