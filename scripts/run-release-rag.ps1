param(
    [Parameter(Mandatory)][string]$Question,
    [Parameter(Mandatory)][string]$ComponentRun,
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$DocumentationRun='',
    [string]$BuildRun='',
    [string]$NativeUiRun='',
    [string]$UiBuildRun='',
    [string]$PublicationEvidence='',
    [switch]$RestrictedPublication,
    [string]$Java='C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe'
)
$ErrorActionPreference='Stop'
if($RestrictedPublication-and-not$PublicationEvidence){throw 'RestrictedPublication requires PublicationEvidence.'}
if($DocumentationRun-and($BuildRun-or$NativeUiRun-or$UiBuildRun-or$PublicationEvidence)){throw 'Documentation reconciliation only verifies offline evidence.'}
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'RuntimeQualification.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
$ragDataRoot=Get-AtfDataRoot -ProjectRoot $project
foreach($path in @($ComponentRun,$DocumentationRun,$BuildRun,$NativeUiRun,$UiBuildRun,$PublicationEvidence)|Where-Object{-not[string]::IsNullOrWhiteSpace($_)}){Assert-AtfArtifactPath -Path $path -ProjectRoot $project}
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
$ComponentRun=[IO.Path]::GetFullPath($ComponentRun);$RunRoot=[IO.Path]::GetFullPath($RunRoot)
if($DocumentationRun){$DocumentationRun=[IO.Path]::GetFullPath($DocumentationRun)}
if($BuildRun){$BuildRun=[IO.Path]::GetFullPath($BuildRun)}
if($NativeUiRun){$NativeUiRun=[IO.Path]::GetFullPath($NativeUiRun)}
if($UiBuildRun){$UiBuildRun=[IO.Path]::GetFullPath($UiBuildRun)}
if($NativeUiRun-and-not($BuildRun-or$UiBuildRun)){throw 'Native UI evidence requires a source-bound UI build receipt.'}
if($UiBuildRun-and-not$NativeUiRun){throw 'UiBuildRun requires NativeUiRun.'}
if((Test-Path -LiteralPath $RunRoot)-or$RunRoot.StartsWith($project+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw 'A fresh external RAG report directory is required.'
}
$reportRoot=Join-Path $RunRoot 'report'
Assert-AtfArtifactPath -Path $reportRoot -ProjectRoot $project
$componentFile=Join-Path $ComponentRun 'result.json'
if(-not(Test-Path -LiteralPath $componentFile -PathType Leaf)){throw 'Component run receipt is missing.'}
$component=Get-Content -Raw -LiteralPath $componentFile|ConvertFrom-Json
if($component.status-notin@('PASS','FAIL')-or$component.collection.name-cne'regression'){
    throw 'RAG accepts only the full regression receipt, whether it passed or failed; it never launches a gate.'
}
$null=New-Item -ItemType Directory -Path $RunRoot
$source=[IO.Path]::GetFullPath($component.source)
if(!$DocumentationRun-and-not$source.Equals($project,[StringComparison]::OrdinalIgnoreCase)){throw 'RAG source root and invoked source snapshot differ.'}
Assert-AtfPhysicalPath -Path $source
$stage=[IO.Path]::GetFullPath($component.stage)
Assert-AtfPhysicalPath -Path $stage
$classFile=Join-Path $stage 'atf-tests/target/classes/atf/rentz/distribution/ReleaseReadinessRag.class'
if(-not(Test-Path -LiteralPath $classFile -PathType Leaf)){throw 'RAG class is absent from the component stage.'}
$ragClass='atf.rentz.distribution.ReleaseReadinessRag'
$classpath=''
if($component.status-eq'PASS'){
    if(-not$component.nativeRuntime-or-not$component.nativeRuntimeSha256){throw 'Passing component evidence requires its qualified runtime receipt.'}
    $native=Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime `
        -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $stage
    $descriptor=Join-Path $RunRoot 'release-rag-runtime.json'

    [ordered]@{mainClass=$ragClass;classpath=$native.classpath;classFile=$classFile;classSha256=(Get-FileHash -LiteralPath $classFile -Algorithm SHA256).Hash.ToLowerInvariant();inventory=$native.inventory;inventorySha256=$native.inventorySha256;attestation=$native.attestation;attestationSha256=$native.attestationSha256;qualificationToken=$native.qualificationToken}|ConvertTo-Json|Set-Content -LiteralPath $descriptor -Encoding utf8NoBOM
    $qualified=Assert-RentzQualifiedRuntime -Descriptor $descriptor -DescriptorSha256 (Get-FileHash -LiteralPath $descriptor -Algorithm SHA256).Hash -MainClass $ragClass -Stage $stage
    $classpath=$qualified.classpath
}else{
    foreach($row in $component.results | Where-Object module -CEQ 'atf-tests'){
        if($row.name-cnotmatch '^[\p{L}\p{N}_$]+(?:[.][\p{L}\p{N}_$]+)*$'){throw 'Invalid ATF Surefire suite name.'}
        $xmlPath=Join-Path $ComponentRun ('atf-tests/TEST-'+$row.name+'.xml')
        Assert-AtfPhysicalPath -Path $xmlPath
        if(-not(Test-Path -LiteralPath $xmlPath -PathType Leaf)){continue}
        $settings=[Xml.XmlReaderSettings]::new();$settings.DtdProcessing=[Xml.DtdProcessing]::Prohibit;$settings.XmlResolver=$null
        $xml=[Xml.XmlDocument]::new();$reader=[Xml.XmlReader]::Create($xmlPath,$settings);try{$xml.Load($reader)}finally{$reader.Dispose()}
        $properties=@($xml.SelectNodes('/testsuite/properties/property[@name="java.class.path"]'))
        if($properties.Count-gt1){throw 'Duplicate ATF Surefire classpath property.'}
        if(!$properties.Count){continue}
        $candidate=$properties[0].GetAttribute('value').TrimEnd([IO.Path]::PathSeparator)
        if(!$candidate){continue}
        if($classpath-and$classpath-cne$candidate){throw 'Failed-run ATF Surefire classpaths disagree.'}
        $classpath=$candidate
    }
    if(-not$classpath){throw 'Failed component run has no ATF Surefire classpath for offline RAG inspection.'}
    $entries=$classpath.Split([IO.Path]::PathSeparator)
    if(@($entries|Where-Object{-not[IO.Path]::IsPathFullyQualified($_)}).Count){throw 'Failed-run ATF classpath contains a non-absolute entry.'}
    $classes=[IO.Path]::GetFullPath((Join-Path $stage 'atf-tests/target/classes'))
    if($classes-notin@($entries|ForEach-Object{[IO.Path]::GetFullPath($_)})){
        throw 'Failed-run ATF classpath does not identify the staged RAG classes.'
    }
}

if($DocumentationRun){
    & $Java -Xmx256m -cp $classpath $ragClass '--reconcile-documentation' $project $reportRoot $ComponentRun $DocumentationRun
    if($LASTEXITCODE-ne0){throw "Documentation reconciliation could not read its evidence: $RunRoot"}
    $proof=Join-Path $reportRoot 'documentation-reconciliation.json'
    $verdict=Get-Content -LiteralPath $proof -Raw|ConvertFrom-Json
    if($verdict.status-cne'PASS'){throw "Documentation reconciliation blocked ($($verdict.reason)): $proof"}
    Write-Output $proof
    return
}

$arguments=[Collections.Generic.List[string]]::new()
$arguments.Add($source);$arguments.Add($reportRoot);$arguments.Add($Question);$arguments.Add($ComponentRun)
if($BuildRun-or$NativeUiRun){$arguments.Add($(if($BuildRun){$BuildRun}else{'-'}))}
if($NativeUiRun){$arguments.Add($NativeUiRun)}
if($UiBuildRun){$arguments.Add($UiBuildRun)}
$ragArguments=@('-Xmx256m','-cp',$classpath,$ragClass)+$arguments.ToArray()
$previousDataRoot=[Environment]::GetEnvironmentVariable('RENTZ_DATA_ROOT','Process')
try{
    [Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$ragDataRoot,'Process')
    & $Java @ragArguments
}finally{
    [Environment]::SetEnvironmentVariable('RENTZ_DATA_ROOT',$previousDataRoot,'Process')
}
if($LASTEXITCODE-ne0){throw "Local release RAG failed: $RunRoot"}
$reportFile=Join-Path $reportRoot 'release-rag-report.json'
if($PublicationEvidence){
    $inputs=@{RagReport=$reportFile;PublicationEvidence=$PublicationEvidence;RunRoot=(Join-Path $RunRoot 'publication')}
    if($RestrictedPublication){
        $inputs.Restricted=$true
        $inputs.ComponentExtent=Join-Path $ComponentRun 'component-suite-extent.html'
        if($NativeUiRun){$inputs.NativeExtent=Join-Path $NativeUiRun 'android-extent.html'}
    }
    & (Join-Path $PSScriptRoot 'verify-release-readme.ps1') @inputs
}
Write-Output $reportFile
