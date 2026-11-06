[CmdletBinding()]
param(
    [string]$RunRoot = '',
    [string]$ContinueFrom = '',
    [switch]$WithAndroid,
    [switch]$ExclusiveDeviceConfirmed,
    [string]$Serial = '127.0.0.1:5555',
    [string]$AdbServerSocket = 'tcp:192.168.56.1:5038',
    [string]$AdbExecutable = 'C:\tools\whist-android-sdk\platform-tools\adb.exe',
    [string]$AndroidSdk = 'C:\tools\whist-android-sdk',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Maven = 'C:\tools\Maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$MavenRepository='',
    [string]$EvidenceRoot = 'C:\work\whist_back\reference-matches',
    [string]$LeasePath = 'C:\work\whist_back\android-ui-lease.json'
)
$ErrorActionPreference = 'Stop'
if (-not $WithAndroid -or -not $ExclusiveDeviceConfirmed) {
    throw 'The reference match requires -WithAndroid and -ExclusiveDeviceConfirmed. No device command was sent.'
}
$j5Root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
$MavenRepository=Resolve-AtfJ5MavenRepository -Repository $MavenRepository
$workspace = Split-Path -Parent $j5Root
$j4Root = [IO.Path]::GetFullPath((Join-Path $workspace 'atf_j4'))
if (-not (Test-Path -LiteralPath (Join-Path $j4Root 'pom.xml'))) { throw "ATF test foundation is missing: $j4Root" }
if (-not $RunRoot) { $RunRoot = Join-Path $EvidenceRoot ((Get-Date -Format 'yyyyMMdd-HHmmss') + '-reference-match-' + [guid]::NewGuid().ToString('N').Substring(0,8)) }
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
foreach ($sourceRoot in @($j4Root, $j5Root)) {
    if ($RunRoot.Equals($sourceRoot,[StringComparison]::OrdinalIgnoreCase) -or $RunRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Run evidence must remain outside J4 and J5.' }
}
if (Test-Path -LiteralPath $RunRoot) { throw "Choose a fresh run folder; previous evidence is never overwritten: $RunRoot" }
if (-not (Test-Path -LiteralPath $MavenRepository -PathType Container)) { throw "Offline Maven cache is missing: $MavenRepository" }
if (-not (Test-Path -LiteralPath $Maven -PathType Leaf)) { throw "Maven executable is missing: $Maven" }
if (-not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe') -PathType Leaf)) { throw "Java 21 is missing: $JavaHome" }

$evidence = Join-Path $RunRoot 'evidence'
$native = Join-Path $RunRoot 'native'
$stage = Join-Path ([IO.Path]::GetTempPath()) ('ATF_J5_REFERENCE_MATCH_' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $evidence,$native,$stage | Out-Null
if ($ContinueFrom) {
    $priorRun = [IO.Path]::GetFullPath($ContinueFrom)
    foreach ($sourceRoot in @($j4Root,$j5Root)) {
        if ($priorRun.Equals($sourceRoot,[StringComparison]::OrdinalIgnoreCase) -or $priorRun.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Continuation evidence must be outside J4 and J5.' }
    }
    $priorEvidence = Join-Path $priorRun 'evidence'
    $priorRecord = Join-Path $priorEvidence 'original-match.properties'
    if (-not (Test-Path -LiteralPath $priorRecord -PathType Leaf)) { throw "No resumable reference match: $priorRecord" }
    $priorText = [IO.File]::ReadAllText($priorRecord)
    if ($priorText.Contains('match.complete=true')) { throw 'A complete reference match is audited, not replayed.' }
    if (-not $priorText.Contains('match.handCount=')) { throw 'The prior run has no durable match checkpoint.' }
    Copy-Item -Path (Join-Path $priorEvidence '*') -Destination $evidence -Recurse
}

Copy-Item -LiteralPath (Join-Path $j4Root 'pom.xml'),(Join-Path $j4Root 'atf.yaml'),(Join-Path $j4Root 'lib'),(Join-Path $j4Root 'config') -Destination $stage -Recurse
foreach ($module in @('atf-test-kit','atf-mock','atf-validation','atf-console','atf-android','atf-test-android')) {
    & robocopy (Join-Path $j4Root $module) (Join-Path $stage $module) /E /XD target build .git .gradle artifacts /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "Could not stage J4 module $module." }
}
New-Item -ItemType Directory -Force -Path (Join-Path $stage 'atf-tests') | Out-Null
Copy-Item -LiteralPath (Join-Path $j4Root 'atf-tests\pom.xml') -Destination (Join-Path $stage 'atf-tests\pom.xml')
& robocopy (Join-Path $j4Root 'whist-lab-app') (Join-Path $stage 'whist-lab-app') /E /XD target build .git .gradle artifacts /NFL /NDL /NJH /NJS /NP | Out-Null
if ($LASTEXITCODE -ge 8) { throw 'Could not stage Whist Lab support sources required by the Maven test module.' }
$referenceSources = Join-Path $j5Root 'reference-tests\src\test\java'
foreach ($file in Get-ChildItem -LiteralPath $referenceSources -File -Recurse) {
    $relative = [IO.Path]::GetRelativePath($referenceSources,$file.FullName)
    $target = Join-Path (Join-Path $stage 'atf-test-android\src\test\java') $relative
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
}
Get-ChildItem -LiteralPath $stage -Recurse -File | ForEach-Object {
    @{file=[IO.Path]::GetRelativePath($stage,$_.FullName);sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $RunRoot 'source-inventory.json') -Encoding utf8

$saved=@{}
foreach ($name in @('JAVA_HOME','Path','MAVEN_OPTS','ADB_SERVER_SOCKET','ATF_ANDROID_ADB','ATF_ANDROID_SERIAL','ATF_ANDROID_TIMEOUT_SECONDS','ANDROID_HOME','ANDROID_SDK_ROOT')) { $saved[$name]=[Environment]::GetEnvironmentVariable($name) }
$leaseId='';$pushed=$false
$state=@{status='STAGED';run=$RunRoot;stage=$stage;package='com.qsoft.android.whist';expectedVersionCode=145;continueFrom=$ContinueFrom;startedAt=[DateTimeOffset]::UtcNow.ToString('o')}
function Invoke-MavenPhase([string]$Name,[string[]]$Arguments) {
    $log=Join-Path $RunRoot ($Name+'.log')
    & $Maven @Arguments *> $log
    if ($LASTEXITCODE -ne 0) { throw "$Name failed; diagnostics remain in $RunRoot. See $log" }
}
try {
    $env:JAVA_HOME=$JavaHome;$env:Path="$JavaHome\bin;$($saved.Path)"
    $env:MAVEN_OPTS='-Xms32m -Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
    $env:ADB_SERVER_SOCKET=$AdbServerSocket;$env:ATF_ANDROID_ADB=$AdbExecutable;$env:ATF_ANDROID_SERIAL=$Serial;$env:ATF_ANDROID_TIMEOUT_SECONDS='95'
    $env:ANDROID_HOME=$AndroidSdk;$env:ANDROID_SDK_ROOT=$AndroidSdk
    Import-Module (Join-Path $j4Root 'scripts\AndroidDeviceSetup.psm1') -Force
    Push-Location $stage;$pushed=$true
    Invoke-MavenPhase 'compile-in-tmp' @('-o','-pl','atf-test-android','-am','test-compile',"-Dmaven.repo.local=$MavenRepository",'-Dsurefire.failIfNoSpecifiedTests=false')
    Connect-AtfAndroidDevice -AdbExecutable $AdbExecutable -Serial $Serial -ServerSocket $AdbServerSocket -TimeoutSeconds 20 | Out-Null
    $packageInfo=& $AdbExecutable -s $Serial shell dumpsys package com.qsoft.android.whist
    if ($LASTEXITCODE -ne 0 -or -not ($packageInfo | Select-String 'versionCode=145\b')) { throw 'The reference state parser is qualified for versionCode 145 only; no game input was sent.' }
    $packageInfo | Select-String 'versionCode=|versionName=' | Set-Content -LiteralPath (Join-Path $RunRoot 'installed-reference-version.txt') -Encoding utf8

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LeasePath) | Out-Null
$leaseStream=[IO.File]::Open($LeasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try {
        $reader=[IO.StreamReader]::new($leaseStream,[Text.Encoding]::UTF8,$true,1024,$true);$existing=$reader.ReadToEnd();$reader.Dispose()
        if (-not [string]::IsNullOrWhiteSpace($existing) -and ($existing | ConvertFrom-Json).active) { throw "Another controller owns the Android device; no game input was sent. Lease: $LeasePath" }
        $leaseId=[guid]::NewGuid().ToString('N')
        $lease=@{active=$true;owner='J5 complete Contract Whist reference match';leaseId=$leaseId;run=$RunRoot;serial=$Serial;startedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json
        $bytes=[Text.Encoding]::UTF8.GetBytes($lease);$leaseStream.SetLength(0);$leaseStream.Position=0;$leaseStream.Write($bytes,0,$bytes.Length);$leaseStream.Flush($true)
    } finally { $leaseStream.Dispose() }

    $state.status='LIVE_RUNNING'
    $properties=@('-Dtest=CompleteOriginalMatchE2eTest','-Datf.android.enabled=true','-Datf.android.fullMatch=true','-Datf.android.exclusiveReady=true',"-Datf.android.leasePath=$LeasePath","-Datf.android.serial=$Serial","-Datf.original.leaseId=$leaseId","-Datf.match.evidenceDir=$evidence")
    if ($ContinueFrom) { $properties += '-Datf.match.continue=true' }
    Invoke-MavenPhase 'reference-match-live' (@('-o','-pl','atf-test-android','-am','test',"-Dmaven.repo.local=$MavenRepository",'-Dsurefire.failIfNoSpecifiedTests=false')+$properties)

    $xmlPath=Join-Path $stage 'atf-test-android\target\surefire-reports\TEST-atf.android.e2e.CompleteOriginalMatchE2eTest.xml'
    if (-not (Test-Path -LiteralPath $xmlPath -PathType Leaf)) { throw "Native test result is missing: $xmlPath" }
    Copy-Item -LiteralPath $xmlPath -Destination $native
    [xml]$xml=Get-Content -LiteralPath $xmlPath -Raw
    if ([int]$xml.testsuite.tests -ne 1 -or [int]$xml.testsuite.failures -ne 0 -or [int]$xml.testsuite.errors -ne 0 -or [int]$xml.testsuite.skipped -ne 0) { throw 'The live reference match did not execute exactly one passing native test.' }
    $record=Join-Path $evidence 'original-match.properties'
    if (-not (Test-Path -LiteralPath $record -PathType Leaf)) { throw "Reference match record is missing: $record" }
    $recordText=[IO.File]::ReadAllText($record)
    foreach ($required in @('match.complete=true','match.handCount=19','match.moves=436','match.tricks=109')) { if (-not $recordText.Contains($required)) { throw "Reference match is incomplete; missing $required" } }
    for ($hand=1;$hand -le 19;$hand++) {
        $snapshot=Join-Path $evidence ('original-h{0:D2}-bidding.save2' -f $hand)
        if (-not (Test-Path -LiteralPath $snapshot -PathType Leaf)) { throw "Reference match snapshot is missing: $snapshot" }
    }
    $extent=Join-Path $evidence 'original-match-extent-report.html'
    if (-not (Test-Path -LiteralPath $extent -PathType Leaf)) { throw "Original-app Extent report is missing: $extent" }
    $extentText=[IO.File]::ReadAllText($extent)
    if ($extentText.Length -lt 2000 -or -not $extentText.Contains('data:image/png;base64,')) { throw "Original-app Extent report is incomplete or lacks inline screenshots: $extent" }
    $state.status='COMPLETE_REFERENCE_MATCH_PASS';$state.nativeResult=$xmlPath;$state.extentReport=$extent;$state.handCount=19;$state.moves=436;$state.tricks=109
} catch {
    $state.status='FAILED';$state.failure=$_.ToString();throw
} finally {
    if ($pushed) { Pop-Location }
    if ($leaseId -and (Test-Path -LiteralPath $LeasePath)) {
        try {
            $release=[IO.File]::Open($LeasePath,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
            try {
                $reader=[IO.StreamReader]::new($release,[Text.Encoding]::UTF8,$true,1024,$true);$content=$reader.ReadToEnd();$reader.Dispose()
                $current=$content | ConvertFrom-Json
                if ($current.leaseId -eq $leaseId) {
                    $current.active=$false;$bytes=[Text.Encoding]::UTF8.GetBytes(($current | ConvertTo-Json))
                    $release.SetLength(0);$release.Position=0;$release.Write($bytes,0,$bytes.Length);$release.Flush($true)
                }
            } finally { $release.Dispose() }
        } catch { $state.status='FAILED';$state.leaseReleaseFailure=$_.ToString();Write-Warning "Could not release this run's lease; inspect $LeasePath before another controller starts." }
    }
    $state.completedAt=[DateTimeOffset]::UtcNow.ToString('o')
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $RunRoot 'WORKING_STATE.json') -Encoding utf8
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name]) }
}
Write-Output $RunRoot