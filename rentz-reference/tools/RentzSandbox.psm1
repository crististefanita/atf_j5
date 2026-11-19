# Preservation is confined to the isolated test package; production packages are never targets.
function Get-RentzSandboxManifest([string]$Path) {
    Add-Type -AssemblyName System.Formats.Tar
    $file=Get-Item -LiteralPath $Path
    if($file.Length-lt1024-or$file.Length-gt64MB-or$file.Length%512){throw 'ANDROID-BACKUP: invalid TAR bounds.'}
    $stream=[IO.File]::OpenRead($Path);$reader=[System.Formats.Tar.TarReader]::new($stream)
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $rows=[Collections.Generic.List[object]]::new()
    try{
        while($entry=$reader.GetNextEntry()){
            $name=$entry.Name
            if($name-match'(^[/\\]|^[A-Za-z]:|\\|(^|/)\.\.(/|$))'){throw 'ANDROID-BACKUP: unsafe TAR member.'}
            $canonical=($name-split'/'|Where-Object{$_-and$_-cne'.'})-join'/'
            if(!$canonical-and$entry.EntryType-eq[System.Formats.Tar.TarEntryType]::Directory){continue}
            if(!$canonical-or!$names.Add($canonical)){throw 'ANDROID-BACKUP: duplicate TAR member.'}
            if($entry.EntryType-eq[System.Formats.Tar.TarEntryType]::Directory){continue}
            if($entry.EntryType-notin@([System.Formats.Tar.TarEntryType]::RegularFile,[System.Formats.Tar.TarEntryType]::V7RegularFile)){throw 'ANDROID-BACKUP: links and special entries are forbidden.'}
            $hash=if($entry.Length-eq0){[Security.Cryptography.SHA256]::HashData([byte[]]::new(0))}else{[Security.Cryptography.SHA256]::HashData($entry.DataStream)}
            $digest=[Convert]::ToHexString([byte[]]$hash).ToLowerInvariant()
            $rows.Add([pscustomobject]@{path=$canonical;bytes=$entry.Length;sha256=$digest})
        }
    }finally{$reader.Dispose();$stream.Dispose()}
    return @($rows|Sort-Object path -CaseSensitive)
}

function Invoke-RentzSandboxCommand($RunRoot,$LeasePath,$Command,[string[]]$Arguments,$Label,[switch]$Binary,[int[]]$Allowed=@(0)) {
    Assert-RentzAndroidReservation -Path $LeasePath -RunRoot $RunRoot|Out-Null
    $result=& $Command @{Arguments=$Arguments;Label=$Label;Binary=[bool]$Binary;AllowedExitCodes=$Allowed}
    Assert-RentzAndroidReservation -Path $LeasePath -RunRoot $RunRoot|Out-Null
    if($result.ExitCode-notin$Allowed){throw "ANDROID-BACKUP: command failed: $Label"}
    return $result
}

function Save-RentzTestSandbox {
    param([string]$RunRoot,[string]$LeasePath,[Parameter(Mandatory)][scriptblock]$Command)
    $ErrorActionPreference='Stop'
    $folder=Join-Path $RunRoot 'sandbox-backup'
    if(Test-Path -LiteralPath $folder){throw 'ANDROID-BACKUP: fresh preservation directory required.'}
    $null=New-Item -ItemType Directory $folder
    $port={param($commandArgs,$label,$binary=$false,$allowed=@(0)) Invoke-RentzSandboxCommand $RunRoot $LeasePath $Command $commandArgs $label -Binary:$binary -Allowed $allowed}
    $pm=& $port @('shell','pm','path','ro.rentzlab.app.uisanity') 'preserve-path' $false @(0,1)
    $identity=[ordered]@{present=$false;package='ro.rentzlab.app.uisanity'}
    if($pm.Output.Trim()){
        if($pm.Output.Trim()-cnotmatch'^package:(/data/app/[A-Za-z0-9_./=+-]+/base.apk)$'){throw 'ANDROID-BACKUP: unexpected package path.'}
        $remote=$Matches[1]
        & $port @('shell','am','force-stop','ro.rentzlab.app.uisanity') 'preserve-stop'|Out-Null
        $apk=& $port @('exec-out','cat',$remote) 'preserve-apk' $true
        Copy-Item -LiteralPath $apk.File -Destination (Join-Path $folder 'original.apk')
        if((Get-Item (Join-Path $folder 'original.apk')).Length-lt1024){throw 'ANDROID-BACKUP: truncated APK.'}
        $tar=& $port @('exec-out','run-as','ro.rentzlab.app.uisanity','tar','-c','.') 'preserve-data' $true
        Copy-Item -LiteralPath $tar.File -Destination (Join-Path $folder 'original-data.tar')
        $identity.present=$true
        $identity.apkSha256=(Get-FileHash (Join-Path $folder 'original.apk')).Hash.ToLowerInvariant()
        $identity.dataSha256=(Get-FileHash (Join-Path $folder 'original-data.tar')).Hash.ToLowerInvariant()
        $identity.files=@(Get-RentzSandboxManifest (Join-Path $folder 'original-data.tar'))
    }
    # Persist verified recovery before the first destructive command, including ambiguous failures.
    $identity|ConvertTo-Json -Depth 6|Set-Content (Join-Path $folder 'identity.json') -Encoding utf8NoBOM
    if($identity.present){& $port @('uninstall','ro.rentzlab.app.uisanity') 'preserve-remove-isolated'|Out-Null}
    return [pscustomobject]$identity
}

function Restore-RentzTestSandbox {
    param([string]$RunRoot,[string]$LeasePath,[string]$TestApkSha256,[Parameter(Mandatory)][scriptblock]$Command)
    $ErrorActionPreference='Stop'
    if($TestApkSha256-cnotmatch'^[a-fA-F0-9]{64}$'){throw 'ANDROID-BACKUP: qualified test APK digest required.'}
    Assert-RentzAndroidReservation -Path $LeasePath -RunRoot $RunRoot|Out-Null
    $folder=Join-Path $RunRoot 'sandbox-backup';$file=Join-Path $folder 'identity.json'
    if(!(Test-Path -LiteralPath $file)){return $true} # Preparation failed before destructive work.
    $identity=Get-Content $file -Raw|ConvertFrom-Json
    if($identity.package-cne'ro.rentzlab.app.uisanity'-or$identity.present-isnot[bool]){throw 'ANDROID-BACKUP: invalid identity.'}
    if($identity.present){
        foreach($pair in @(@('original.apk','apkSha256'),@('original-data.tar','dataSha256'))){
            if((Get-FileHash (Join-Path $folder $pair[0])).Hash-ine$identity.($pair[1])){throw 'ANDROID-BACKUP: recovery bytes changed.'}
        }
        $null=Get-RentzSandboxManifest (Join-Path $folder 'original-data.tar')
    }
    $port={param($commandArgs,$label,$binary=$false,$allowed=@(0)) Invoke-RentzSandboxCommand $RunRoot $LeasePath $Command $commandArgs $label -Binary:$binary -Allowed $allowed}
    $pm=& $port @('shell','pm','path','ro.rentzlab.app.uisanity') 'restore-current-path' $false @(0,1)
    if($pm.Output.Trim()){
        if($pm.Output.Trim()-cnotmatch'^package:(/data/app/[A-Za-z0-9_./=+-]+/base.apk)$'){throw 'ANDROID-BACKUP: unexpected current package.'}
        $current=& $port @('exec-out','cat',$Matches[1]) 'restore-current-apk' $true
        $hash=(Get-FileHash $current.File).Hash.ToLowerInvariant()
        if($hash-cne$TestApkSha256.ToLowerInvariant()-and(!$identity.present-or$hash-cne$identity.apkSha256)){throw 'ANDROID-BACKUP: foreign APK; no uninstall or overwrite.'}
        if(!$identity.present-or$hash-cne$identity.apkSha256){& $port @('uninstall','ro.rentzlab.app.uisanity') 'restore-remove-owned-test'|Out-Null}
    }
    $manifest=@()
    if($identity.present){
        & $port @('install','-r',(Join-Path $folder 'original.apk')) 'restore-original-apk'|Out-Null
        $own=Get-Content (Join-Path $RunRoot 'lease.json') -Raw|ConvertFrom-Json
        $remote='/data/local/tmp/rentz-preserved-'+$own.leaseId+'.tar'
        try{
            & $port @('push',(Join-Path $folder 'original-data.tar'),$remote) 'restore-push-data'|Out-Null
            & $port @('shell','run-as','ro.rentzlab.app.uisanity','tar','-xof',$remote) 'restore-extract-data'|Out-Null
            $actual=& $port @('exec-out','run-as','ro.rentzlab.app.uisanity','tar','-c','.') 'restore-verify-data' $true
            $manifest=@(Get-RentzSandboxManifest $actual.File)
            if((ConvertTo-Json -InputObject $manifest -Depth 5 -Compress)-cne(ConvertTo-Json -InputObject @($identity.files) -Depth 5 -Compress)){throw 'ANDROID-BACKUP: restored regular-file bytes differ.'}
        }finally{& $port @('shell','rm','-f',$remote) 'restore-remove-owned-archive'|Out-Null}
    }
    [ordered]@{status='PASS';originalPresent=$identity.present;files=$manifest;scope='Regular-file content; archive UID and timestamps excluded'}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $folder 'restored.json') -Encoding utf8NoBOM
    return $true
}
Export-ModuleMember -Function Save-RentzTestSandbox,Restore-RentzTestSandbox
