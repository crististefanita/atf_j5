param(
    [Parameter(Mandatory)][string]$AndroidRun,
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$JavaHome='C:/tools/Java21/jdk-21.0.12.1+1'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
if(Test-Path -LiteralPath $RunRoot){throw 'Calibration requires a fresh output directory.'}
$receipt=Get-Content -LiteralPath (Join-Path $AndroidRun 'result.json') -Raw|ConvertFrom-Json
$test=@($receipt.tests|Where-Object name -EQ 'ro.rentzlab.app.achievements.GameplayAchievementComponentTest')
if($receipt.status-cne'PASS'-or$test.Count-ne1-or$test[0].tests-lt5-or$test[0].failures-or$test[0].errors-or$test[0].skipped){
    throw 'A passing gameplay Android component receipt is required.'
}
$stage=[IO.Path]::GetFullPath($receipt.stage)
$manifest=Get-Content -LiteralPath (Join-Path $AndroidRun 'source.json') -Raw|ConvertFrom-Json
foreach($entry in $manifest){
    $path=Join-Path $stage $entry.path
    if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash-ine$entry.sha256){
        throw "Android staged source changed since component verification: $($entry.path)"
    }
}
$classes=Join-Path $stage 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
$testClasses=Join-Path $stage 'app/build/intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes'
$runner=Join-Path $testClasses 'ro/rentzlab/app/achievements/CalibrateGameplayAchievements.class'
if(-not(Test-Path -LiteralPath $runner -PathType Leaf)){throw 'The offline simulator must have been compiled by the qualified Android JVM stage.'}
$core=Join-Path $stage 'game-core/build/libs/game-core.jar'
$names=Join-Path $stage 'ui-library/ro/cardtable/card-table-names/0.1.0/card-table-names-0.1.0.jar'
$gsonDirectory=Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/2.11.0'
$gson=@(Get-ChildItem -LiteralPath $gsonDirectory -Recurse -File -Filter 'gson-2.11.0.jar')
if($gson.Count-ne1){throw 'Expected a single resolved Gson binary from the Android cache.'}
$plan=Join-Path $stage 'config/rentz-achievement-calibration.yaml'
$rules=Join-Path $stage 'app/src/main/achievement-rules/rentz-gameplay-achievements.yaml'
$null=New-Item -Path $RunRoot -ItemType Directory
$cp=@($testClasses,$classes,$core,$names,$gson[0].FullName)-join[IO.Path]::PathSeparator
$summary=Join-Path $RunRoot 'summary.json'
& "$JavaHome/bin/java.exe" -Xmx512m -cp $cp ro.rentzlab.app.achievements.CalibrateGameplayAchievements $plan $summary
if($LASTEXITCODE){throw 'Calibration did not complete.'}
$data=Get-Content -LiteralPath $summary -Raw|ConvertFrom-Json
$catalog=Get-Content -LiteralPath $rules -Raw|ConvertFrom-Json
$expected=@($catalog.tiers.EASY)+@($catalog.tiers.MEDIUM)+@($catalog.tiers.HARD)
if($data.games-lt200-or($data.achievements.id -join ',')-cne($expected.id -join ',')){
    throw 'Calibration did not cover the full catalog and match plan.'
}
[ordered]@{
    status='PASS'
    scope='Offline full Rentz matches and independent holdout'
    androidComponentReceipt=(Join-Path $AndroidRun 'result.json')
    catalogSha256=(Get-FileHash -LiteralPath $rules -Algorithm SHA256).Hash.ToLowerInvariant()
    planSha256=(Get-FileHash -LiteralPath $plan -Algorithm SHA256).Hash.ToLowerInvariant()
    summarySha256=(Get-FileHash -LiteralPath $summary -Algorithm SHA256).Hash.ToLowerInvariant()
    games=$data.games
    hands=$data.hands
    decisions=$data.decisions
}|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $RunRoot 'result.json') -Encoding utf8NoBOM
Write-Output $RunRoot
