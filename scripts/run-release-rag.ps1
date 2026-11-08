param(
    [Parameter(Mandatory)][string]$Question,
    [Parameter(Mandatory)][string]$ComponentRun,
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$Java='C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe'
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'RuntimeQualification.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $ComponentRun -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
$ComponentRun=[IO.Path]::GetFullPath($ComponentRun)
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
if((Test-Path -LiteralPath $RunRoot)-or$RunRoot.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw 'A fresh external RAG report directory is required.'
}
$reportRoot=Join-Path $RunRoot 'report'
Assert-AtfArtifactPath -Path $reportRoot -ProjectRoot $project
$componentFile=Join-Path $ComponentRun 'result.json'
$component=Get-Content -Raw -LiteralPath $componentFile|ConvertFrom-Json
if($component.status-cne'PASS'-or$component.collection.name-cne'regression'){
    throw 'RAG reads only a completed full regression receipt; it never launches test gates.'
}
if(-not$component.nativeRuntime-or-not$component.nativeRuntimeSha256){throw 'A qualified offline runtime receipt is required.'}
$source=[IO.Path]::GetFullPath($component.source)
Assert-AtfPhysicalPath -Path $source
$runtime=Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime `
    -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage
$ragClass='atf.rentz.distribution.ReleaseReadinessRag'
$classFile=Join-Path $component.stage 'atf-tests/target/classes/atf/rentz/distribution/ReleaseReadinessRag.class'
if(-not(Test-Path -LiteralPath $classFile -PathType Leaf)){throw 'RAG class is absent from the qualified source snapshot.'}
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$descriptor=Join-Path $RunRoot 'release-rag-runtime.json'
[ordered]@{
    mainClass=$ragClass
    classpath=$runtime.classpath
    classFile=$classFile
    classSha256=(Get-FileHash -LiteralPath $classFile -Algorithm SHA256).Hash.ToLowerInvariant()
    inventory=$runtime.inventory
    inventorySha256=$runtime.inventorySha256
    attestation=$runtime.attestation
    attestationSha256=$runtime.attestationSha256
    qualificationToken=$runtime.qualificationToken
}|ConvertTo-Json|Set-Content -LiteralPath $descriptor -Encoding utf8NoBOM
$runtime=Assert-RentzQualifiedRuntime -Descriptor $descriptor `
    -DescriptorSha256 (Get-FileHash -LiteralPath $descriptor -Algorithm SHA256).Hash `
    -MainClass $ragClass -Stage $component.stage
$evidenceRun=[IO.Path]::GetFullPath($ComponentRun)
& $Java -Xmx256m -cp $runtime.classpath $ragClass $source $reportRoot $Question $evidenceRun
if($LASTEXITCODE-ne0){throw "Local release RAG failed: $RunRoot"}
Write-Output (Join-Path $reportRoot 'release-rag-report.json')
