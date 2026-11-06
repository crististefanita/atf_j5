param(
    [Parameter(Mandatory)][string]$ComponentRun,
    [Parameter(Mandatory)][string]$Protocol,
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$Registry = 'C:\work\rentz_back\learning\exposure.json',
    [string]$Java = 'C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
Assert-AtfArtifactPath -Path $Registry -ProjectRoot $project
$ComponentRun=[IO.Path]::GetFullPath($ComponentRun)
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
$Registry=[IO.Path]::GetFullPath($Registry)
foreach($destination in @($RunRoot,$Registry)){
    if($destination.Equals($project,[StringComparison]::OrdinalIgnoreCase)-or$destination.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Learning artifacts belong outside the source tree.'}
}
if(Test-Path -LiteralPath $RunRoot){throw 'A fresh arena directory is required.'}
$receipt=Get-Content -Raw -LiteralPath (Join-Path $ComponentRun 'result.json')|ConvertFrom-Json
if($receipt.status-ne'PASS'){throw 'A passing frozen component run is required.'}
$stage=[IO.Path]::GetFullPath($receipt.stage)
if(-not(Test-Path -LiteralPath $stage -PathType Container)){throw 'Frozen compiled snapshot is unavailable; do not replace it with HEAD.'}
if(-not$receipt.arenaRuntime-or-not$receipt.arenaRuntimeSha256-or(Get-FileHash -LiteralPath $receipt.arenaRuntime).Hash-ne$receipt.arenaRuntimeSha256){throw 'Qualified arena runtime descriptor required'}
$runtime=Get-Content -Raw -LiteralPath $receipt.arenaRuntime|ConvertFrom-Json
if($runtime.mainClass-ne'atf.rentz.OfflineArena'-or$runtime.classFile-ne(Join-Path $stage 'atf-tests/target/classes/atf/rentz/OfflineArena.class')-or(Get-FileHash -LiteralPath $runtime.classFile).Hash-ne$runtime.classSha256){throw 'Arena compiled class identity differs'}
$classpath=$runtime.classpath
if(-not$classpath){throw 'Effective tested classpath missing.'}
$entries=@($classpath.Split([IO.Path]::PathSeparator)|Where-Object {-not[string]::IsNullOrWhiteSpace($_)})
$classpath=$entries -join [IO.Path]::PathSeparator
Import-Module (Join-Path $project 'scripts/AtfMavenCache.psm1') -Force
$cacheLease=Enter-AtfJ5CacheLease -Repository $receipt.repository
try {
$identity=@(foreach($entry in $entries){
    if(Test-Path -LiteralPath $entry -PathType Leaf){[pscustomobject]@{path=$entry;sha256=(Get-FileHash -LiteralPath $entry -Algorithm SHA256).Hash}}
    elseif(-not(Test-Path -LiteralPath $entry -PathType Container)){throw "Classpath entry missing: $entry"}
})
New-Item -ItemType Directory -Force -Path (Split-Path $RunRoot) | Out-Null
& $Java -Xmx256m -XX:ActiveProcessorCount=2 -cp $classpath atf.rentz.OfflineArena $stage ([IO.Path]::GetFullPath($Protocol)) $RunRoot $Registry (Join-Path $ComponentRun 'source-identity.json') *> ($RunRoot+'.log')
$exitCode=$LASTEXITCODE
if(Test-Path -LiteralPath $RunRoot){
    $identity|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $RunRoot 'classpath-identity.json') -Encoding utf8NoBOM
    $drift=@(foreach($row in $identity){if((Get-FileHash -LiteralPath $row.path -Algorithm SHA256).Hash-ne$row.sha256){$row.path}})
    [ordered]@{exitCode=$exitCode;componentRun=$ComponentRun;stage=$stage;classpathDrift=$drift;protocolSha256=(Get-FileHash -LiteralPath $Protocol -Algorithm SHA256).Hash;scriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $RunRoot 'execution.json') -Encoding utf8NoBOM
    if($drift.Count){throw 'Classpath binaries changed during the arena run.'}
}
if($exitCode-ne0){throw "Arena not qualified; inspect $RunRoot.log"}
} finally {$cacheLease.Dispose()}
Write-Output $RunRoot
