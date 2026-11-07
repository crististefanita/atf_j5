[CmdletBinding(DefaultParameterSetName='Arena')]
param(
    [Parameter(Mandatory)][string]$ComponentRun,
    [Parameter(Mandatory,ParameterSetName='Arena')][string]$Protocol,
    [Parameter(Mandatory,ParameterSetName='Arena')][string]$RunRoot,
    [Parameter(Mandatory,ParameterSetName='Initialize')][string]$ReviewedHistory,
    [Parameter(Mandatory,ParameterSetName='Initialize')][string]$Reviewer,
    [Parameter(Mandatory,ParameterSetName='Initialize')][string]$ReviewReference,
    [string]$Registry,
    [string]$Java = 'C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
$installationRegistry=[IO.Path]::GetFullPath((Join-Path (Get-AtfDataRoot -ProjectRoot $project) 'learning/exposure.json'))
if($Registry -and -not [IO.Path]::GetFullPath($Registry).Equals($installationRegistry,[StringComparison]::OrdinalIgnoreCase)){
    throw 'Arena must use the installation exposure registry.'
}
$Registry=$installationRegistry
Assert-AtfArtifactPath -Path $Registry -ProjectRoot $project
$ComponentRun=[IO.Path]::GetFullPath($ComponentRun)
if($PSCmdlet.ParameterSetName -eq 'Arena'){
    $RunRoot=[IO.Path]::GetFullPath($RunRoot)
    Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
    if(Test-Path -LiteralPath $RunRoot){throw 'A fresh arena directory is required.'}
}
$receipt=Get-Content -Raw -LiteralPath (Join-Path $ComponentRun 'result.json')|ConvertFrom-Json
if($receipt.status-ne'PASS'){throw 'A passing frozen component run is required.'}
$stage=[IO.Path]::GetFullPath($receipt.stage)
if(-not(Test-Path -LiteralPath $stage -PathType Container)){throw 'Frozen compiled snapshot is unavailable; do not replace it with HEAD.'}
Import-Module (Join-Path $project 'scripts/RuntimeQualification.psm1') -Force
$runtime=Assert-RentzQualifiedRuntime -Descriptor $receipt.arenaRuntime -DescriptorSha256 $receipt.arenaRuntimeSha256 -MainClass 'atf.rentz.OfflineArena' -Stage $stage
$classpath=$runtime.classpath
if(-not$classpath){throw 'Effective tested classpath missing.'}
$entries=@($classpath.Split([IO.Path]::PathSeparator)|Where-Object {-not[string]::IsNullOrWhiteSpace($_)})
$classpath=$entries -join [IO.Path]::PathSeparator
Import-Module (Join-Path $project 'scripts/AtfMavenCache.psm1') -Force
$cacheLease=Enter-AtfJ5CacheLease -Repository $receipt.repository
try {
if($PSCmdlet.ParameterSetName -eq 'Initialize'){
    & $Java -Xmx256m -cp $classpath atf.rentz.OfflineArena initialize-registry $stage $Registry ([IO.Path]::GetFullPath($ReviewedHistory)) $Reviewer $ReviewReference
    if($LASTEXITCODE -ne 0){throw 'Exposure registry initialization failed.'}
    Assert-RentzQualifiedRuntime -Descriptor $receipt.arenaRuntime -DescriptorSha256 $receipt.arenaRuntimeSha256 -MainClass 'atf.rentz.OfflineArena' -Stage $stage|Out-Null
    return
}
$identity=@(foreach($entry in $entries){
    if(Test-Path -LiteralPath $entry -PathType Leaf){[pscustomobject]@{path=$entry;sha256=(Get-FileHash -LiteralPath $entry -Algorithm SHA256).Hash}}
    elseif(-not(Test-Path -LiteralPath $entry -PathType Container)){throw "Classpath entry missing: $entry"}
})
New-Item -ItemType Directory -Force -Path (Split-Path $RunRoot) | Out-Null
Assert-RentzQualifiedRuntime -Descriptor $receipt.arenaRuntime -DescriptorSha256 $receipt.arenaRuntimeSha256 -MainClass 'atf.rentz.OfflineArena' -Stage $stage|Out-Null
& $Java -Xmx256m -XX:ActiveProcessorCount=2 -cp $classpath atf.rentz.OfflineArena $stage ([IO.Path]::GetFullPath($Protocol)) $RunRoot $Registry (Join-Path $ComponentRun 'source-identity.json') *> ($RunRoot+'.log')
$exitCode=$LASTEXITCODE
$runtimeFailure=''
try{Assert-RentzQualifiedRuntime -Descriptor $receipt.arenaRuntime -DescriptorSha256 $receipt.arenaRuntimeSha256 -MainClass 'atf.rentz.OfflineArena' -Stage $stage|Out-Null}catch{$runtimeFailure=$_.Exception.Message}

if(Test-Path -LiteralPath $RunRoot){
    $identity|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $RunRoot 'classpath-identity.json') -Encoding utf8NoBOM
    $drift=@(foreach($row in $identity){if((Get-FileHash -LiteralPath $row.path -Algorithm SHA256).Hash-ne$row.sha256){$row.path}})
    [ordered]@{exitCode=$exitCode;componentRun=$ComponentRun;stage=$stage;classpathDrift=$drift;protocolSha256=(Get-FileHash -LiteralPath $Protocol -Algorithm SHA256).Hash;scriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash;qualifiedRuntimeFailure=$runtimeFailure}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $RunRoot 'execution.json') -Encoding utf8NoBOM
    if($drift.Count){throw 'Classpath binaries changed during the arena run.'}
}
if($runtimeFailure){throw $runtimeFailure}
if($exitCode-ne0){throw "Arena not qualified; inspect $RunRoot.log"}
} finally {$cacheLease.Dispose()}
Write-Output $RunRoot
