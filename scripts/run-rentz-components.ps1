param(
    [string]$RunRoot = '',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Maven = 'C:\tools\Maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$MavenRepository = '',
    [string]$SourceRoot = '',
    [ValidateSet('quick','regression','learning','infrastructure','android-jvm')][string]$Collection='regression'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $root
$source=$root
$sourceCommit=''
if($SourceRoot){
    $source=[IO.Path]::GetFullPath($SourceRoot)
    $sourceCommit=Get-AtfReleaseSourceCommit -SourceRoot $source -ProjectRoot $root
}
$checkoutLayout=Get-AtfCheckoutLayoutRoot -ProjectRoot $source
$reportingLibrary=Get-AtfReportingLibrary -ProjectRoot $source
Import-Module (Join-Path $PSScriptRoot 'TestCollections.psm1') -Force
if (-not $RunRoot) { $RunRoot = Join-Path (Join-Path (Get-AtfDataRoot -ProjectRoot $root) 'components') ((Get-Date -Format yyyyMMdd-HHmmss)+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)) }
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $root
if ($RunRoot.Equals($root,[StringComparison]::OrdinalIgnoreCase) -or $RunRoot.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Results belong outside the source project.' }
if (Test-Path -LiteralPath $RunRoot) { throw 'A fresh run directory is required.' }
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
$repository = Resolve-AtfJ5MavenRepository -Repository $MavenRepository
$stage = New-AtfWorkspace -Kind components -ProjectRoot $root
New-Item -ItemType Directory -Path $RunRoot | Out-Null
$infrastructureControls=Join-Path (Get-AtfInfrastructurePath -Area governance -ProjectRoot $root) ('component-controls-'+[guid]::NewGuid().ToString('N'))
& (Join-Path $root 'atf-tests/tools/test-workspace-governance.ps1') -RunRoot $infrastructureControls *> (Join-Path $RunRoot 'infrastructure-controls.log')
if($LASTEXITCODE-ne0){throw "Infrastructure controls failed; inspect $infrastructureControls"}
$runtimeControls=Join-Path (Get-AtfInfrastructurePath -Area governance -ProjectRoot $root) ('runtime-controls-'+[guid]::NewGuid().ToString('N'))
& (Join-Path $root 'atf-tests/tools/test-runtime-controls.ps1') -RunRoot $runtimeControls *> (Join-Path $RunRoot 'runtime-controls.log')
if($LASTEXITCODE-ne0){throw "Runtime controls failed; inspect $runtimeControls"}
$discoveryControls=Join-Path (Get-AtfInfrastructurePath -Area governance -ProjectRoot $root) ('discovery-controls-'+[guid]::NewGuid().ToString('N'))
& (Join-Path $root 'atf-tests/tools/test-test-discovery.ps1') -RunRoot $discoveryControls *> (Join-Path $RunRoot 'discovery-controls.log')
if($LASTEXITCODE-ne0){throw "Discovery controls failed; inspect $discoveryControls"}
$qualifiedRuntimeControls=Join-Path (Get-AtfInfrastructurePath -Area governance -ProjectRoot $root) ('qualified-runtime-controls-'+[guid]::NewGuid().ToString('N'))
& (Join-Path $root 'atf-tests/tools/test-qualified-runtime.ps1') -RunRoot $qualifiedRuntimeControls *> (Join-Path $RunRoot 'qualified-runtime-controls.log')
if($LASTEXITCODE-ne0){throw "Qualified runtime controls failed; inspect $qualifiedRuntimeControls"}
$parts = @('pom.xml','README.md','.gitattributes','.gitignore','library-provenance.json','lib','atf-tests','rentz-lab-app','config','docs','release','reference-tests/README.md','rentz-reference','scripts')
$manifest = [Collections.Generic.List[object]]::new()
foreach ($part in $parts) {
    $origin = Join-Path $source $part
    if (Test-Path -LiteralPath $origin -PathType Leaf) { $files=@(Get-Item -LiteralPath $origin) }
    else { $files=@(Get-ChildItem -LiteralPath $origin -Recurse -File | Where-Object { [IO.Path]::GetRelativePath($origin,$_.FullName) -notmatch '(^|[\\/])(target|build|\.gradle|\.git|node_modules)([\\/]|$)' }) }
    foreach ($file in $files) {
        $relative=[IO.Path]::GetRelativePath($source,$file.FullName)
        $target=Join-Path $stage $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
        $hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash) { throw 'Source changed during copy.' }
        $manifest.Add([pscustomobject]@{path=$relative.Replace('\','/');sha256=$hash})
    }
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath "$RunRoot\source-identity.json" -Encoding utf8NoBOM
$plan=Get-RentzTestPlan -ProjectRoot $stage -Collection $Collection -InventorySourceRoot $stage -JavaHome $JavaHome
$runtimeInventory=Join-Path $RunRoot 'classpath-inventory.json'
$runtimeAttestation=Join-Path $RunRoot 'runtime-attestation.json'
$qualificationToken=[guid]::NewGuid().ToString('N')
$savedJava=$env:JAVA_HOME
$exit=1
$cacheLease=Enter-AtfJ5CacheLease -Repository $repository
try {
    $env:JAVA_HOME=$JavaHome
    Push-Location $stage
    try {
        New-Item -ItemType Directory -Path "$stage/runtime"|Out-Null
        $arguments=@('-B','-o',"-Drentz.runtime.inventory=$runtimeInventory","-Drentz.runtime.attestation=$runtimeAttestation","-Drentz.runtime.token=$qualificationToken","-Drentz.governance.sourceRoot=$source","-Drentz.governance.checkoutLayout=$checkoutLayout","-Dmaven.repo.local=$repository","-Djava.io.tmpdir=$stage/runtime",'-Datf.test.heap.max=256m',"-Drentz.report.output=$RunRoot\rentz-rules-extent.html","-Drentz.release.report.directory=$RunRoot\release-contracts")
        if($plan.selectors.Count){$arguments+=@('-Dsurefire.failIfNoSpecifiedTests=false',('-Dtest='+($plan.selectors-join',')),'test')}else{$arguments+=@('-N','validate')}
        & $Maven @arguments *> "$RunRoot\maven.log"
        $exit=$LASTEXITCODE
    } finally { Pop-Location }
} finally { $env:JAVA_HOME=$savedJava; $cacheLease.Dispose() }
$results=@()
$reportFailures=[Collections.Generic.List[string]]::new()
foreach ($module in @('rentz-lab-app/game-core','atf-tests')) {
    $reports=Join-Path $stage "$module/target/surefire-reports"
    $destination=Join-Path $RunRoot ($module.Replace('/','-'))
    New-Item -ItemType Directory -Path $destination | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        Copy-Item -LiteralPath $file.FullName -Destination $destination
        try{
            $row=Read-RentzSurefireResult -Path $file.FullName -Module $module -Plan $plan
            if($file.Name -cne ('TEST-'+$row.name+'.xml')){throw 'Surefire filename and suite name disagree'}
            $results+=$row
        }catch{$reportFailures.Add("$module/$($file.Name) - $($_.Exception.Message)")}
    }
}
$populationFailure=$reportFailures -join [Environment]::NewLine
if(-not $populationFailure){
    try{Assert-RentzTestPopulation -Plan $plan -Results $results}catch{$populationFailure=$_.Exception.Message}
}
$runtimeQualificationFailure=''
if($exit-eq0-and-not$populationFailure-and@($results|Where-Object module -EQ 'atf-tests').Count){
    try{
        Import-Module (Join-Path $PSScriptRoot 'RuntimeQualification.psm1') -Force
        $testedClasspaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        foreach($row in $results|Where-Object module -EQ 'atf-tests'){
            [xml]$testXml=Get-Content -Raw -LiteralPath (Join-Path $RunRoot ('atf-tests/TEST-'+$row.name+'.xml'))
            $testClasspath=($testXml.testsuite.properties.property|Where-Object name -EQ 'java.class.path').value
            [void]$testedClasspaths.Add([string]$testClasspath)
        }
        if($testedClasspaths.Count-ne1){throw 'One attested classpath required for the qualified test plan'}
        $attestedClasspath=@($testedClasspaths)[0]
        Assert-RentzRuntimeAttestation -Inventory $runtimeInventory -Attestation $runtimeAttestation -Token $qualificationToken -Classpath $attestedClasspath
        $qualifiedClasspath=(Get-Content -Raw -LiteralPath $runtimeAttestation|ConvertFrom-Json).classpath
    }catch{$runtimeQualificationFailure=$_.Exception.Message}
}
$androidRun='';$androidFailure=''
if($exit-eq0-and-not$populationFailure-and-not$runtimeQualificationFailure-and$plan.androidJvm){
    $androidRun=Join-Path $RunRoot 'android-jvm'
    try{& (Join-Path $PSScriptRoot 'test-rentz-android-jvm.ps1') -SourceRoot $stage -RunRoot $androidRun -JavaHome $JavaHome -MavenRepository $repository *> "$RunRoot/android-jvm.log";if($LASTEXITCODE-ne0){throw 'Android JVM stage failed'}}catch{$androidFailure=$_.Exception.Message}
}
$drift=@(foreach($row in $manifest){if(-not(Test-Path -LiteralPath (Join-Path $source $row.path))-or(Get-FileHash -LiteralPath (Join-Path $source $row.path) -Algorithm SHA256).Hash -ne $row.sha256){$row.path}})
$afterPaths=@(foreach($part in $parts){
    $origin=Join-Path $source $part
    if(Test-Path -LiteralPath $origin -PathType Leaf){$files=@(Get-Item -LiteralPath $origin)}
    else{$files=@(Get-ChildItem -LiteralPath $origin -Recurse -File | Where-Object {[IO.Path]::GetRelativePath($origin,$_.FullName) -notmatch '(^|[\\/])(target|build|\.gradle|\.git|node_modules)([\\/]|$)'})}
    foreach($file in $files){[IO.Path]::GetRelativePath($source,$file.FullName).Replace('\','/')}
})
$populationChanges=@(Compare-Object @($manifest.path | Sort-Object) @($afterPaths | Sort-Object))
if($sourceCommit){
    try{if((Get-AtfReleaseSourceCommit -SourceRoot $source -ProjectRoot $root)-cne$sourceCommit){$drift+='Release source commit changed'}}
    catch{$drift+=$_.Exception.Message}
}
$healthy=$exit -eq 0 -and -not$runtimeQualificationFailure -and -not$populationFailure -and -not$androidFailure -and $drift.Count -eq 0
$healthy=$healthy -and $populationChanges.Count -eq 0
$nativeRuntime='';$nativeRuntimeSha256=''
if($healthy-and@($results|Where-Object name -EQ 'atf.rentz.android.RentzNativeFlowTest').Count){
    [xml]$nativeTest=Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'atf-tests/TEST-atf.rentz.android.RentzNativeFlowTest.xml')
    $testedClasspath=($nativeTest.testsuite.properties.property|Where-Object{$_.name-eq'java.class.path'}).value
    if(-not$testedClasspath){throw 'Native adapter needs its tested classpath.'}
    $entries=@($testedClasspath.Split([IO.Path]::PathSeparator)|Where-Object{-not[string]::IsNullOrWhiteSpace($_)})
    if(-not$entries.Count){throw 'Native adapter classpath has no explicit entries.'}
    $classpath=$qualifiedClasspath
    $adapterSource=Join-Path $stage 'atf-tests/src/test/java/atf/rentz/android/RentzNativeFlow.java'
    $adapterClass=Join-Path $stage 'atf-tests/target/test-classes/atf/rentz/android/RentzNativeFlow.class'
    $nativeRuntime=Join-Path $RunRoot 'native-runtime.json'
    [ordered]@{mainClass='atf.rentz.android.RentzNativeFlow';classpath=$classpath;source=$adapterSource;sourceSha256=(Get-FileHash -LiteralPath $adapterSource -Algorithm SHA256).Hash;classFile=$adapterClass;classSha256=(Get-FileHash -LiteralPath $adapterClass -Algorithm SHA256).Hash;inventory=$runtimeInventory;inventorySha256=(Get-FileHash $runtimeInventory).Hash;attestation=$runtimeAttestation;attestationSha256=(Get-FileHash $runtimeAttestation).Hash;qualificationToken=$qualificationToken;scope='Compiled test adapter, qualified offline; no Android execution implied'}|ConvertTo-Json|Set-Content -LiteralPath $nativeRuntime -Encoding utf8NoBOM
    $nativeRuntimeSha256=(Get-FileHash -LiteralPath $nativeRuntime -Algorithm SHA256).Hash
}
$arenaRuntime='';$arenaRuntimeSha256=''
if($healthy-and@($results|Where-Object name -EQ 'atf.rentz.OfflineArenaComponentTest').Count){
    [xml]$arenaTest=Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'atf-tests/TEST-atf.rentz.OfflineArenaComponentTest.xml')
    $classpath=$qualifiedClasspath
    $arenaClass=Join-Path $stage 'atf-tests/target/classes/atf/rentz/OfflineArena.class'
    $arenaRuntime=Join-Path $RunRoot 'arena-runtime.json'
    [ordered]@{mainClass='atf.rentz.OfflineArena';classpath=$classpath;classFile=$arenaClass;classSha256=(Get-FileHash $arenaClass).Hash;inventory=$runtimeInventory;inventorySha256=(Get-FileHash $runtimeInventory).Hash;attestation=$runtimeAttestation;attestationSha256=(Get-FileHash $runtimeAttestation).Hash;qualificationToken=$qualificationToken}|ConvertTo-Json|Set-Content -LiteralPath $arenaRuntime -Encoding utf8NoBOM
    $arenaRuntimeSha256=(Get-FileHash $arenaRuntime).Hash
}
$sourceManifestSha256=(Get-FileHash -LiteralPath "$RunRoot\source-identity.json").Hash.ToLowerInvariant()
$componentExtentReport=Join-Path $RunRoot 'component-suite-extent.html'
$componentExtentReportSha256='';$componentExtentReportFailure='';$componentExtentQualified=$false
$extentSuites=[Collections.Generic.List[object]]::new()
$extentInputFailures=[Collections.Generic.List[string]]::new()
foreach($row in $results){
    $relativeModule=$row.module.Replace('/','-')
    $xmlPath=Join-Path (Join-Path $RunRoot $relativeModule) ('TEST-'+$row.name+'.xml')
    if(-not(Test-Path -LiteralPath $xmlPath -PathType Leaf)){$extentInputFailures.Add('Selected Surefire XML is missing: '+$row.name);continue}
    $extentSuites.Add([ordered]@{module=$row.module;name=$row.name;xml=[IO.Path]::GetFullPath($xmlPath);sha256=(Get-FileHash -LiteralPath $xmlPath -Algorithm SHA256).Hash.ToLowerInvariant();tests=$row.tests;failures=$row.failed;errors=$row.errors;skipped=$row.skipped})
}
if($androidRun-and(Test-Path -LiteralPath (Join-Path $androidRun 'result.json') -PathType Leaf)){
    $androidReceipt=Get-Content -Raw -LiteralPath (Join-Path $androidRun 'result.json')|ConvertFrom-Json
    foreach($row in $androidReceipt.tests){
        $xmlPath=Join-Path $androidRun ('TEST-'+$row.name+'.xml')
        if(-not(Test-Path -LiteralPath $xmlPath -PathType Leaf)){$extentInputFailures.Add('Android JVM Surefire XML is missing: '+$row.name);continue}
        $extentSuites.Add([ordered]@{module='android-jvm';name=$row.name;xml=[IO.Path]::GetFullPath($xmlPath);sha256=(Get-FileHash -LiteralPath $xmlPath -Algorithm SHA256).Hash.ToLowerInvariant();tests=$row.tests;failures=$row.failures;errors=$row.errors;skipped=$row.skipped})
    }
}
if(-not$componentExtentReportFailure){
    try{
        $reportClasspath=''
        $reportRows=@($results|Where-Object module -EQ 'atf-tests')
        foreach($row in $reportRows){
            $xmlPath=Join-Path (Join-Path $RunRoot $row.module.Replace('/','-')) ('TEST-'+$row.name+'.xml')
            [xml]$xml=Get-Content -Raw -LiteralPath $xmlPath
            $value=[string](($xml.testsuite.properties.property|Where-Object name -EQ 'java.class.path'|Select-Object -First 1).value)
            if($value){if($reportClasspath-and$reportClasspath-cne$value){throw 'Surefire test classpaths disagree'};$reportClasspath=$value}
        }
        if(-not$reportRows.Count-or-not$reportClasspath){throw 'No ATF test-module classpath is present in Surefire XML'}
        $sourceCommitForReport=$sourceCommit
        if(-not$sourceCommitForReport){$sourceCommitForReport=[string](git -C $source rev-parse HEAD);if($LASTEXITCODE-ne0){throw 'Source commit unavailable for Extent report'}}
        $executionDiagnostics=[Collections.Generic.List[string]]::new()
        foreach($diagnostic in @($androidFailure,$runtimeQualificationFailure,$populationFailure)){if(-not[string]::IsNullOrWhiteSpace($diagnostic)){$executionDiagnostics.Add($diagnostic)}}
        if($exit-ne0){$executionDiagnostics.Add("Maven exited with code $exit")}
        foreach($diagnostic in $extentInputFailures){$executionDiagnostics.Add($diagnostic)}
        $executionDiagnostic=$executionDiagnostics -join '; '
        $reportExitCode=if($androidFailure-or$runtimeQualificationFailure-or$populationFailure-or$extentInputFailures.Count-or$exit-ne0){1}else{0}
        $reportInput=Join-Path $RunRoot 'component-suite-input.json'
        [ordered]@{schemaVersion=1;sourceCommit=$sourceCommitForReport.ToLowerInvariant();sourceManifestSha256=$sourceManifestSha256;collection=$plan.name;processExitCode=$reportExitCode;executionFailure=$executionDiagnostic;suites=@($extentSuites)}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $reportInput -Encoding utf8NoBOM
        $javaReport=Join-Path $JavaHome 'bin/java.exe'
        & $javaReport -Xmx256m -cp $reportClasspath atf.rentz.reporting.ComponentSuiteExtentReport $reportInput $componentExtentReport *> (Join-Path $RunRoot 'component-suite-extent.log')
        if($LASTEXITCODE-ne0){throw 'atf-reporting suite report publisher failed'}
        $extentSummary=Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'component-suite-extent.log')|Select-Object -Last 1|ConvertFrom-Json
        if(-not(Test-Path -LiteralPath $componentExtentReport -PathType Leaf)-or-not$extentSummary.reportSha256){throw 'Extent output or digest is missing'}
        $componentExtentReportSha256=(Get-FileHash -LiteralPath $componentExtentReport -Algorithm SHA256).Hash.ToLowerInvariant()
        if($componentExtentReportSha256-cne$extentSummary.reportSha256){throw 'Extent report digest differs from publisher receipt'}
        $componentExtentQualified=[bool]$extentSummary.qualified
    }catch{$componentExtentReportFailure=$_.Exception.Message}
}
if($componentExtentReportFailure){$healthy=$false}else{$healthy=$healthy-and$componentExtentQualified}
$resultReceipt=[ordered]@{status=$(if($healthy){'PASS'}else{'FAIL'});collection=$plan;source=$source;sourceCommit=$sourceCommit;sourceManifestSha256=$sourceManifestSha256;checkoutLayout=$checkoutLayout;stage=$stage;repository=$repository;infrastructureControls=$infrastructureControls;runtimeControls=$runtimeControls;discoveryControls=$discoveryControls;qualifiedRuntimeControls=$qualifiedRuntimeControls;runtimeQualificationFailure=$runtimeQualificationFailure;nativeRuntime=$nativeRuntime;nativeRuntimeSha256=$nativeRuntimeSha256;arenaRuntime=$arenaRuntime;arenaRuntimeSha256=$arenaRuntimeSha256;androidJvm=$androidRun;androidFailure=$androidFailure;selectedPopulationFailure=$populationFailure;componentExtentReport=$componentExtentReport;componentExtentReportSha256=$componentExtentReportSha256;componentExtentReportFailure=$componentExtentReportFailure;componentExtentQualified=$componentExtentQualified;componentExtentReportingLibrary=$reportingLibrary.name;componentExtentReportingLibrarySha256=$reportingLibrary.sha256;results=$results;drift=$drift;populationChanges=$populationChanges;scope='Selected offline components; reference parity and Android E2E are not qualified.'}
$resultReceipt | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath "$RunRoot\result.json" -Encoding utf8NoBOM
if (-not $healthy) { throw "Component run not qualified; inspect $RunRoot" }
Write-Output $RunRoot
