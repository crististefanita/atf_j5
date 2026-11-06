param([Parameter(Mandatory)][string]$SourceRoot,[Parameter(Mandatory)][string]$RunRoot,[string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1',[string]$Sdk='C:\tools\whist-android-sdk',[string]$MavenRepository='')
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
if(Test-Path -LiteralPath $RunRoot){throw 'Fresh Android JVM run required'}
$stage=New-AtfWorkspace -Kind android -ProjectRoot $project
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$lab=Join-Path $SourceRoot 'rentz-lab-app'
$manifest=@(foreach($file in Get-ChildItem -LiteralPath $lab -Recurse -File|Where-Object{[IO.Path]::GetRelativePath($lab,$_.FullName)-notmatch'(^|[\\/])(build|target|\.gradle|\.git)([\\/]|$)'-and$_.Name-ne'local.properties'}){
    $relative=[IO.Path]::GetRelativePath($lab,$file.FullName);$target=Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target)|Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target
    $hash=(Get-FileHash -LiteralPath $file.FullName).Hash
    if((Get-FileHash -LiteralPath $target).Hash-ne$hash){throw 'Source changed while staging'}
    [pscustomobject]@{path=$relative.Replace('\','/');sha256=$hash}
})
$manifest|ConvertTo-Json|Set-Content -LiteralPath "$RunRoot/source.json" -Encoding utf8NoBOM
$stage|Set-Content -LiteralPath "$RunRoot/stage.txt"
('sdk.dir='+$Sdk.Replace('\','/'))|Set-Content -LiteralPath "$stage/local.properties" -Encoding ascii
$repository=Resolve-AtfJ5MavenRepository -Repository $MavenRepository
$lease=Enter-AtfJ5CacheLease -Repository $repository
$savedJava=$env:JAVA_HOME;$savedOptions=$env:JAVA_TOOL_OPTIONS;$exit=1
try{
    $env:JAVA_HOME=$JavaHome;$runtime=Join-Path $stage 'runtime';New-Item -ItemType Directory -Path $runtime|Out-Null
    $env:JAVA_TOOL_OPTIONS='-Djava.io.tmpdir='+$runtime
    & (Join-Path $stage 'gradlew.bat') -p $stage --offline --no-daemon ('-Prentz.mavenRepository='+$repository.Replace('\','/')) :app:testDebugUnitTest *> "$RunRoot/gradle.log"
    $exit=$LASTEXITCODE
}finally{$env:JAVA_HOME=$savedJava;$env:JAVA_TOOL_OPTIONS=$savedOptions;$lease.Dispose()}
$tests=@(foreach($file in Get-ChildItem -LiteralPath "$stage/app/build/test-results" -Recurse -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue){
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $RunRoot $file.Name)
    [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
    [pscustomobject]@{name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
})
$expected=@(Get-ChildItem -LiteralPath "$stage/app/src/test/java" -Recurse -Filter '*Test.java' -File|ForEach-Object{[IO.Path]::GetRelativePath("$stage/app/src/test/java",$_.FullName).Replace('\','.').Replace('/','.').Replace('.java','')})
$population=@(Compare-Object @($expected|Sort-Object) @($tests.name|Sort-Object))
$drift=@(foreach($row in $manifest){if((Get-FileHash -LiteralPath (Join-Path $lab $row.path)).Hash-ne$row.sha256){$row.path}})
$healthy=$exit-eq0-and$tests.Count-gt0-and@($tests|Where-Object{$_.tests-lt1-or$_.failures-or$_.errors-or$_.skipped}).Count-eq0-and$population.Count-eq0-and$drift.Count-eq0
[ordered]@{status=$(if($healthy){'PASS'}else{'FAIL'});exitCode=$exit;stage=$stage;source=$SourceRoot;tests=$tests;drift=$drift;populationChanges=$population;scope='Android JVM components only; no APK assembly, installation, ADB or emulator'}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath "$RunRoot/result.json" -Encoding utf8NoBOM
if(-not$healthy){throw 'Android JVM components not qualified'}
Write-Output $RunRoot
