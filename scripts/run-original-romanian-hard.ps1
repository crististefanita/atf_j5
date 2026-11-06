param(
    [string]$RunRoot='', [switch]$WithAndroid, [switch]$ExclusiveDeviceConfirmed,
    [string]$ContinueFrom='',
    [string]$TestHeapMax='768m',
    [string]$ModeLabel='Romanian', [string]$Serial='127.0.0.1:5555',
    [string]$AdbServerSocket='tcp:192.168.56.1:5038',
    [string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Maven='C:\tools\Maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$MavenRepository='',
    [string]$AdbExecutable='C:\tools\whist-android-sdk\platform-tools\adb.exe',
    [string]$WhistLabRoot=''
)
$ErrorActionPreference='Stop'
if($WithAndroid -and -not $ExclusiveDeviceConfirmed){throw 'Live play requires confirmed exclusive emulator availability. Offline preparation sends no ADB commands.'}
$j5Root=Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
$MavenRepository=Resolve-AtfJ5MavenRepository -Repository $MavenRepository
$workspace=Split-Path -Parent $j5Root
if(-not $WhistLabRoot){$root=Join-Path $workspace 'atf_j4'}else{$root=[IO.Path]::GetFullPath($WhistLabRoot)}
if(-not (Test-Path -LiteralPath $root)){throw "Whist Lab ATF source is missing: $root"}
if(-not $RunRoot){$RunRoot='C:\work\whist_back\original-hard-live\'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)}
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
if($RunRoot.StartsWith([IO.Path]::GetFullPath($root)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Evidence belongs outside the source project.'}
if(Test-Path -LiteralPath $RunRoot){throw 'A fresh run folder is required; partial matches and failures are never overwritten.'}
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
$stage=New-AtfWorkspace -Kind reference -ProjectRoot $j5Root
New-Item -ItemType Directory -Path $RunRoot | Out-Null
Copy-Item -LiteralPath "$root\pom.xml","$root\atf.yaml","$root\lib","$root\config" -Destination $stage -Recurse
foreach($module in @('atf-test-kit','atf-mock','atf-validation','atf-console','atf-android','atf-test-android')){
    foreach($file in Get-ChildItem -LiteralPath "$root\$module" -File -Recurse){
        $relative=[IO.Path]::GetRelativePath("$root\$module",$file.FullName)
        if(($relative -split '[\\/]') | Where-Object {$_ -in @('target','build','.git','.gradle','artifacts')}){continue}
        $target=Join-Path "$stage\$module" $relative;New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target
    }
}
New-Item -ItemType Directory -Force -Path "$stage\atf-tests" | Out-Null;Copy-Item -LiteralPath "$root\atf-tests\pom.xml" -Destination "$stage\atf-tests\pom.xml"
foreach($tree in @('game-core\src\main\java','ai-lab\src\main\java')){foreach($file in Get-ChildItem -LiteralPath "$root\whist-lab-app\$tree" -File -Recurse){$relative=[IO.Path]::GetRelativePath("$root\whist-lab-app",$file.FullName);$target=Join-Path "$stage\whist-lab-app" $relative;New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target}}
$referenceSources=Join-Path $j5Root 'reference-tests\src\test\java'
foreach($file in Get-ChildItem -LiteralPath $referenceSources -File -Recurse){$relative=[IO.Path]::GetRelativePath($referenceSources,$file.FullName);$target=Join-Path "$stage\atf-test-android\src\test\java" $relative;New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target}
Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {@{file=[IO.Path]::GetRelativePath($stage,$_.FullName);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$RunRoot\source-inventory.json" -Encoding utf8
$saved=@{};foreach($key in @('JAVA_HOME','Path','MAVEN_OPTS','ADB_SERVER_SOCKET','ATF_ANDROID_ADB','ATF_ANDROID_SERIAL','ATF_ANDROID_TIMEOUT_SECONDS')){$saved[$key]=[Environment]::GetEnvironmentVariable($key)}
$leaseId='';$leasePath='C:\work\whist_back\android-ui-lease.json'
$state=@{status='OFFLINE_COMPONENTS_RUNNING';startedAt=[DateTimeOffset]::UtcNow.ToString('o');run=$RunRoot;stage=$stage;package='com.qsoft.android.whist';liveRequested=$WithAndroid.IsPresent;mode=$ModeLabel;difficulty='Hard';players=4;schedule='1x4,2..7,8x4,7..2,1x4';uiAcceptance='NOT_RUN'}
function Invoke-MavenPhase([string]$Label,[string[]]$Properties){
    & $Maven -o -pl atf-test-android -am test "-Dmaven.repo.local=$MavenRepository" '-Dsurefire.failIfNoSpecifiedTests=false' "-Datf.test.heap.max=$TestHeapMax" @Properties *> "$RunRoot\$Label.log"
    $exit=$LASTEXITCODE
    $native="$stage\atf-test-android\target\surefire-reports"
    $copy="$RunRoot\native-$Label"
    $classes=if($Label -eq 'original-live'){@('OriginalRomanianHardLiveE2eTest')}else{@('OriginalRomanianPublicDecisionTest','OriginalRomanianAuditTest','OriginalRomanianDecisionReceiptTest','OriginalRomanianTraceReplayTest','OriginalWhistStateTest')}
    if(Test-Path -LiteralPath $native){
        New-Item -ItemType Directory -Force -Path $copy | Out-Null
        foreach($class in $classes){$xml="$native\TEST-atf.android.e2e.$class.xml";if(Test-Path -LiteralPath $xml){Copy-Item -LiteralPath $xml -Destination $copy}}
    }
    if($exit -ne 0){throw "$Label failed; original evidence retained in $RunRoot"}
    foreach($class in $classes){
        $report=Join-Path $copy "TEST-atf.android.e2e.$class.xml"
        if(-not (Test-Path -LiteralPath $report)){throw "$Label omitted native results for $class; completion is not qualified"}
        [xml]$result=Get-Content -LiteralPath $report -Raw
        $suite=$result.testsuite
        if([int]$suite.tests -lt 1 -or [int]$suite.failures -ne 0 -or [int]$suite.errors -ne 0 -or [int]$suite.skipped -ne 0){throw "$Label has missing, failed or skipped execution in $class"}
    }
}
Push-Location $stage
try{
    $env:JAVA_HOME=$JavaHome;$env:Path="$JavaHome\bin;$($saved.Path)";$env:MAVEN_OPTS='-Xms32m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
    Invoke-MavenPhase 'offline-components' @('-Dtest=OriginalRomanianPublicDecisionTest,OriginalRomanianAuditTest,OriginalRomanianDecisionReceiptTest,OriginalRomanianTraceReplayTest,OriginalWhistStateTest','-Datf.android.enabled=false')
    $state.status='PREPARED_WAITING_FOR_EXCLUSIVE_DEVICE'
    if($WithAndroid){
        $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        try{$reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$existing=$reader.ReadToEnd();$reader.Dispose();if($existing -and ($existing | ConvertFrom-Json).active){throw 'Another controller owns the emulator; no UI command sent'};$leaseId=[guid]::NewGuid().ToString('N');$lease=@{active=$true;owner='J5 reference app Romanian Hard live';leaseId=$leaseId;run=$RunRoot;serial=$Serial;startedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json;$bytes=[Text.Encoding]::UTF8.GetBytes($lease);$stream.SetLength(0);$stream.Position=0;$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
        $env:ADB_SERVER_SOCKET=$AdbServerSocket;$env:ATF_ANDROID_ADB=$AdbExecutable;$env:ATF_ANDROID_SERIAL=$Serial;$env:ATF_ANDROID_TIMEOUT_SECONDS='95'
        $package=& $AdbExecutable -s $Serial shell dumpsys package com.qsoft.android.whist
        $package | Select-String 'versionCode=|versionName=' | Set-Content -LiteralPath "$RunRoot\installed-original-version.txt"
        if($LASTEXITCODE -ne 0 -or -not ($package | Select-String 'versionCode=145\b')){throw 'Original state parser is qualified for installed version145 only; requalify another version before game inputs'}
        $state.status='LIVE_RUNNING';$state.uiAcceptance='RUNNING'
        $liveProperties=@('-Dtest=OriginalRomanianHardLiveE2eTest','-Datf.android.enabled=true','-Datf.original.live=true','-Datf.original.exclusiveReady=true',"-Datf.original.leaseId=$leaseId","-Datf.original.modeLabel=$ModeLabel","-Datf.original.evidence=$RunRoot\evidence")
        if($ContinueFrom){$liveProperties+="-Datf.original.continueFrom=$([IO.Path]::GetFullPath($ContinueFrom))"}
        Invoke-MavenPhase 'original-live' $liveProperties
        $outcome=Get-Content -LiteralPath "$RunRoot\evidence\original-hard-outcome.json" -Raw | ConvertFrom-Json
        if(-not $outcome.complete -or $outcome.hands -ne 24 -or $outcome.ruleAndScoreStatus -ne 'PASS' -or -not $outcome.humanStrictWin){throw 'Native execution did not prove the complete rules-correct match and requested strict victory; retain its actual outcome'}
        $state.status='COMPLETE_ORIGINAL_MATCH_AND_VICTORY_PASS';$state.uiAcceptance='PASS'
    }
}catch{$state.status='FAILED';$state.failure=$_.ToString();throw}
finally{
    try{if($leaseId -and (Test-Path -LiteralPath $leasePath)){
        $release=[IO.File]::Open($leasePath,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        try{
            $reader=[IO.StreamReader]::new($release,[Text.Encoding]::UTF8,$true,1024,$true);$content=$reader.ReadToEnd();$reader.Dispose()
            $current=$content | ConvertFrom-Json
            if($current.leaseId -eq $leaseId){
                $current.active=$false
                $bytes=[Text.Encoding]::UTF8.GetBytes(($current | ConvertTo-Json))
                $release.SetLength(0);$release.Position=0;$release.Write($bytes,0,$bytes.Length);$release.Flush($true)
            }
        }finally{$release.Dispose()}
    }}catch{$state.leaseReleaseFailure=$_.ToString();$state.status='FAILED';Write-Warning "Could not release own device lease; inspect $leasePath before another controller starts."}
    $state.completedAt=[DateTimeOffset]::UtcNow.ToString('o');$state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$RunRoot\WORKING_STATE.json" -Encoding utf8
    Pop-Location;foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key])}
}
Write-Output $RunRoot
