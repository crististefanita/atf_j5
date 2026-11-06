param(
    [string]$RunRoot = '',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Maven = 'C:\tools\Maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$MavenRepository = '',
    [ValidateSet('quick','regression','learning','infrastructure','android-jvm')][string]$Collection='regression'
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $root
Import-Module (Join-Path $PSScriptRoot 'TestCollections.psm1') -Force
$plan=Get-RentzTestPlan -ProjectRoot $root -Collection $Collection
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
$parts = @('pom.xml','README.md','.gitattributes','.gitignore','library-provenance.json','lib','atf-tests','rentz-lab-app','config','docs','release','reference-tests/README.md','scripts')
$manifest = [Collections.Generic.List[object]]::new()
foreach ($part in $parts) {
    $origin = Join-Path $root $part
    if (Test-Path -LiteralPath $origin -PathType Leaf) { $files=@(Get-Item -LiteralPath $origin) }
    else { $files=@(Get-ChildItem -LiteralPath $origin -Recurse -File | Where-Object { [IO.Path]::GetRelativePath($origin,$_.FullName) -notmatch '(^|[\\/])(target|build|\.gradle|\.git|node_modules)([\\/]|$)' }) }
    foreach ($file in $files) {
        $relative=[IO.Path]::GetRelativePath($root,$file.FullName)
        $target=Join-Path $stage $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
        $hash=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $hash) { throw 'Source changed during copy.' }
        $manifest.Add([pscustomobject]@{path=$relative.Replace('\','/');sha256=$hash})
    }
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath "$RunRoot\source-identity.json" -Encoding utf8NoBOM
$savedJava=$env:JAVA_HOME
$exit=1
$cacheLease=Enter-AtfJ5CacheLease -Repository $repository
try {
    $env:JAVA_HOME=$JavaHome
    Push-Location $stage
    try {
        New-Item -ItemType Directory -Path "$stage/runtime"|Out-Null
        $arguments=@('-B','-o',"-Dmaven.repo.local=$repository","-Djava.io.tmpdir=$stage/runtime",'-Datf.test.heap.max=256m',"-Drentz.report.output=$RunRoot\rentz-rules-extent.html","-Drentz.release.report.directory=$RunRoot\release-contracts")
        if($plan.selectors.Count){$arguments+=@('-Dsurefire.failIfNoSpecifiedTests=false',('-Dtest='+($plan.selectors-join',')),'test')}else{$arguments+=@('-N','validate')}
        & $Maven @arguments *> "$RunRoot\maven.log"
        $exit=$LASTEXITCODE
    } finally { Pop-Location }
} finally { $env:JAVA_HOME=$savedJava; $cacheLease.Dispose() }
$results=@()
foreach ($module in @('rentz-lab-app/game-core','atf-tests')) {
    $reports=Join-Path $stage "$module/target/surefire-reports"
    $destination=Join-Path $RunRoot ($module.Replace('/','-'))
    New-Item -ItemType Directory -Path $destination | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        Copy-Item -LiteralPath $file.FullName -Destination $destination
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $results+=[pscustomobject]@{module=$module;name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failed=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
    }
}
$populationFailure=''
try{Assert-RentzTestPopulation -Plan $plan -Results $results}catch{$populationFailure=$_.Exception.Message}
$androidRun='';$androidFailure=''
if($exit-eq0-and-not$populationFailure-and$plan.androidJvm){
    $androidRun=Join-Path $RunRoot 'android-jvm'
    try{& (Join-Path $PSScriptRoot 'test-rentz-android-jvm.ps1') -SourceRoot $stage -RunRoot $androidRun -JavaHome $JavaHome -MavenRepository $repository *> "$RunRoot/android-jvm.log";if($LASTEXITCODE-ne0){throw 'Android JVM stage failed'}}catch{$androidFailure=$_.Exception.Message}
}
$drift=@(foreach($row in $manifest){if(-not(Test-Path -LiteralPath (Join-Path $root $row.path))-or(Get-FileHash -LiteralPath (Join-Path $root $row.path) -Algorithm SHA256).Hash -ne $row.sha256){$row.path}})
$afterPaths=@(foreach($part in $parts){
    $origin=Join-Path $root $part
    if(Test-Path -LiteralPath $origin -PathType Leaf){$files=@(Get-Item -LiteralPath $origin)}
    else{$files=@(Get-ChildItem -LiteralPath $origin -Recurse -File | Where-Object {[IO.Path]::GetRelativePath($origin,$_.FullName) -notmatch '(^|[\\/])(target|build|\.gradle|\.git|node_modules)([\\/]|$)'})}
    foreach($file in $files){[IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/')}
})
$populationChanges=@(Compare-Object @($manifest.path | Sort-Object) @($afterPaths | Sort-Object))
$healthy=$exit -eq 0 -and -not$populationFailure -and -not$androidFailure -and $drift.Count -eq 0
$healthy=$healthy -and $populationChanges.Count -eq 0
$nativeRuntime='';$nativeRuntimeSha256=''
if($healthy-and@($results|Where-Object name -EQ 'atf.rentz.android.RentzNativeFlowTest').Count){
    [xml]$nativeTest=Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'atf-tests/TEST-atf.rentz.android.RentzNativeFlowTest.xml')
    $testedClasspath=($nativeTest.testsuite.properties.property|Where-Object{$_.name-eq'java.class.path'}).value
    if(-not$testedClasspath){throw 'Native adapter needs its tested classpath.'}
    $entries=@($testedClasspath.Split([IO.Path]::PathSeparator)|Where-Object{-not[string]::IsNullOrWhiteSpace($_)})
    if(-not$entries.Count){throw 'Native adapter classpath has no explicit entries.'}
    $classpath=$entries -join [IO.Path]::PathSeparator
    $adapterSource=Join-Path $stage 'atf-tests/src/test/java/atf/rentz/android/RentzNativeFlow.java'
    $adapterClass=Join-Path $stage 'atf-tests/target/test-classes/atf/rentz/android/RentzNativeFlow.class'
    $nativeRuntime=Join-Path $RunRoot 'native-runtime.json'
    [ordered]@{mainClass='atf.rentz.android.RentzNativeFlow';classpath=$classpath;source=$adapterSource;sourceSha256=(Get-FileHash -LiteralPath $adapterSource -Algorithm SHA256).Hash;classFile=$adapterClass;classSha256=(Get-FileHash -LiteralPath $adapterClass -Algorithm SHA256).Hash;scope='Compiled test adapter, qualified offline; no Android execution implied'}|ConvertTo-Json|Set-Content -LiteralPath $nativeRuntime -Encoding utf8NoBOM
    $nativeRuntimeSha256=(Get-FileHash -LiteralPath $nativeRuntime -Algorithm SHA256).Hash
}
$arenaRuntime='';$arenaRuntimeSha256=''
if($healthy-and@($results|Where-Object name -EQ 'atf.rentz.OfflineArenaComponentTest').Count){
    [xml]$arenaTest=Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'atf-tests/TEST-atf.rentz.OfflineArenaComponentTest.xml')
    $classpath=(@((($arenaTest.testsuite.properties.property|Where-Object name -EQ 'java.class.path').value).Split([IO.Path]::PathSeparator)|Where-Object{-not[string]::IsNullOrWhiteSpace($_)})-join[IO.Path]::PathSeparator)
    $arenaClass=Join-Path $stage 'atf-tests/target/classes/atf/rentz/OfflineArena.class'
    $arenaRuntime=Join-Path $RunRoot 'arena-runtime.json'
    [ordered]@{mainClass='atf.rentz.OfflineArena';classpath=$classpath;classFile=$arenaClass;classSha256=(Get-FileHash $arenaClass).Hash}|ConvertTo-Json|Set-Content -LiteralPath $arenaRuntime -Encoding utf8NoBOM
    $arenaRuntimeSha256=(Get-FileHash $arenaRuntime).Hash
}
[ordered]@{status=$(if($healthy){'PASS'}else{'FAIL'});collection=$plan;source=$root;stage=$stage;repository=$repository;infrastructureControls=$infrastructureControls;runtimeControls=$runtimeControls;nativeRuntime=$nativeRuntime;nativeRuntimeSha256=$nativeRuntimeSha256;arenaRuntime=$arenaRuntime;arenaRuntimeSha256=$arenaRuntimeSha256;androidJvm=$androidRun;androidFailure=$androidFailure;selectedPopulationFailure=$populationFailure;results=$results;drift=$drift;populationChanges=$populationChanges;scope='Selected offline components; reference parity and Android E2E are not qualified.'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$RunRoot\result.json" -Encoding utf8NoBOM
if (-not $healthy) { throw "Component run not qualified; inspect $RunRoot" }
Write-Output $RunRoot
