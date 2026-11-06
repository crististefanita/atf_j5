param(
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$ComponentRun,
    [string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Sdk='C:\tools\whist-android-sdk',
    [ValidateSet('Debug','Release')][string]$Configuration='Debug'
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $root
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $root
if($RunRoot.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)-or$RunRoot-eq$root-or(Test-Path -LiteralPath $RunRoot)){throw 'A fresh external build directory is required.'}
$component=Get-Content -Raw -LiteralPath (Join-Path $ComponentRun 'result.json')|ConvertFrom-Json
if($component.status-ne'PASS'){throw 'Qualified offline component source required.'}
if($component.collection.name-ne'regression'-or-not$component.androidJvm){throw 'Full offline regression including Android JVM is required before assembling an APK.'}
$lab=Join-Path $root 'rentz-lab-app'
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
$stage=New-AtfWorkspace -Kind android -ProjectRoot $root
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$stage|Set-Content -LiteralPath (Join-Path $RunRoot 'stage.txt') -Encoding utf8NoBOM
$manifest=@(foreach($file in Get-ChildItem -LiteralPath $lab -Recurse -File|Where-Object{[IO.Path]::GetRelativePath($lab,$_.FullName)-notmatch'(^|[\\/])(build|target|\.gradle|\.git)([\\/]|$)'-and$_.Name-ne'local.properties'}){
    $relative=[IO.Path]::GetRelativePath($lab,$file.FullName);$target=Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target)|Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target
    $hash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
    if($hash-ne(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash){throw 'Source changed during Android snapshot'}
    if($relative.Replace('\','/').StartsWith('game-core/src/main/')){
        $qualified=Join-Path $component.stage ('rentz-lab-app/'+$relative)
        if($hash-ne(Get-FileHash -LiteralPath $qualified -Algorithm SHA256).Hash){throw 'Android core differs from qualified offline production source'}
    }
    [pscustomobject]@{path=$relative.Replace('\','/');sha256=$hash}
})
ConvertTo-Json -InputObject $manifest -Depth 4|Set-Content -LiteralPath (Join-Path $RunRoot 'source-identity.json') -Encoding utf8NoBOM
('sdk.dir='+$Sdk.Replace('\','/'))|Set-Content -LiteralPath (Join-Path $stage 'local.properties') -Encoding ascii
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
$cacheLease=Enter-AtfJ5CacheLease -Repository $component.repository
$savedJava=$env:JAVA_HOME;$savedStore=$env:RENTZ_STORE_FILE;$savedPassword=$env:RENTZ_STORE_PASSWORD;$exit=1
try{
    $env:JAVA_HOME=$JavaHome
    if($Configuration-eq'Release'){
        $signing=Get-AtfInfrastructurePath -Area signing -ProjectRoot $root
        $identity=Get-Content -Raw -LiteralPath (Join-Path $signing 'identity.json')|ConvertFrom-Json
        $secure=(Get-Content -Raw -LiteralPath $identity.credential).Trim()|ConvertTo-SecureString
        $env:RENTZ_STORE_PASSWORD=[Net.NetworkCredential]::new('rentz',$secure).Password
        $env:RENTZ_STORE_FILE=$identity.store
    }
    & (Join-Path $stage 'gradlew.bat') -p $stage --offline --no-daemon ("-Prentz.mavenRepository="+$component.repository.Replace('\','/')) :game-core:test :app:testDebugUnitTest (':app:assemble'+$Configuration) *> (Join-Path $RunRoot 'build.log')
    $exit=$LASTEXITCODE
}finally{$env:JAVA_HOME=$savedJava;$env:RENTZ_STORE_FILE=$savedStore;$env:RENTZ_STORE_PASSWORD=$savedPassword;$secure=$null;$cacheLease.Dispose()}
$results=@(foreach($module in @('game-core','app')){
    foreach($file in Get-ChildItem -LiteralPath (Join-Path $stage "$module/build/test-results") -Filter 'TEST-*.xml' -Recurse -ErrorAction SilentlyContinue){
        $destination=Join-Path $RunRoot ("tests/$module/"+$file.Name);New-Item -ItemType Directory -Force -Path (Split-Path $destination)|Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $destination
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        [pscustomobject]@{module=$module;name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failures=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
    }
})
$drift=@(foreach($row in $manifest){if((Get-FileHash -LiteralPath (Join-Path $lab $row.path) -Algorithm SHA256).Hash-ne$row.sha256){$row.path}})
$after=@(Get-ChildItem -LiteralPath $lab -Recurse -File|Where-Object{[IO.Path]::GetRelativePath($lab,$_.FullName)-notmatch'(^|[\\/])(build|target|\.gradle|\.git)([\\/]|$)'-and$_.Name-ne'local.properties'}|ForEach-Object{[IO.Path]::GetRelativePath($lab,$_.FullName).Replace('\','/')})
$population=@(Compare-Object @($manifest.path|Sort-Object) @($after|Sort-Object))
$passed=$exit-eq0-and$results.Count-ge2-and@($results|Where-Object{$_.failures-or$_.errors-or$_.skipped-or$_.tests-lt1}).Count-eq0-and@($results|Where-Object{$_.module-eq'app'}).Count-gt0-and$drift.Count-eq0-and$population.Count-eq0
$apk=Join-Path $RunRoot 'rentz-lab-debug.apk';$hash='';$certificate=@();$version=$null;$debuggable=$null
if($passed){
    $variant=$Configuration.ToLowerInvariant()
    $version=(Get-Content -Raw -LiteralPath (Join-Path $stage "app/build/outputs/apk/$variant/output-metadata.json")|ConvertFrom-Json).elements[0]
    if($Configuration-eq'Release'){$apk=Join-Path $RunRoot ('RentzLab-'+$version.versionName+'.apk')}
    Copy-Item -LiteralPath (Join-Path $stage "app/build/outputs/apk/$variant/app-$variant.apk") -Destination $apk
    $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    & (Join-Path $Sdk 'build-tools/36.0.0/apksigner.bat') verify --verbose --print-certs $apk *> (Join-Path $RunRoot 'apk-signature.txt');$signed=$LASTEXITCODE-eq0
    & (Join-Path $Sdk 'build-tools/36.0.0/aapt.exe') dump badging $apk *> (Join-Path $RunRoot 'apk-badging.txt');$identified=$LASTEXITCODE-eq0
    $certificate=@(Get-Content -LiteralPath (Join-Path $RunRoot 'apk-signature.txt')|Where-Object{$_-match'certificate SHA-256 digest:'})
    $passed=$passed-and$signed-and$identified-and$certificate.Count-eq1
    & (Join-Path $Sdk 'build-tools/36.0.0/zipalign.exe') -c -P 16 -v 4 $apk *> (Join-Path $RunRoot 'apk-alignment.txt')
    $passed=$passed-and$LASTEXITCODE-eq0
    $debuggable=[bool](@(Get-Content -LiteralPath (Join-Path $RunRoot 'apk-badging.txt')|Where-Object{$_-eq'application-debuggable'}).Count)
    if($Configuration-eq'Release'-and$certificate.Count-eq1){
        $actual=($certificate[0]-split':\s*',2)[1].Trim().ToLowerInvariant()
        $passed=$passed-and-not$debuggable-and$actual-eq$identity.certificateSha256
    }elseif($Configuration-eq'Release'){$passed=$false}
}
[ordered]@{status=$(if($passed){'PASS'}else{'FAIL'});configuration=$Configuration;debuggable=$debuggable;stage=$stage;componentRun=$ComponentRun;qualifiedCoreMatches=$true;builderSha256=(Get-FileHash -LiteralPath $PSCommandPath).Hash;sourceManifestSha256=(Get-FileHash -LiteralPath (Join-Path $RunRoot 'source-identity.json')).Hash;apkFile=[IO.Path]::GetFileName($apk);apkSha256=$hash;versionName=$version.versionName;versionCode=$version.versionCode;certificate=$certificate;signing=$(if($Configuration-eq'Release'){'Dedicated Rentz release identity; external DPAPI credential'}else{'Local Android debug certificate; not an original-app release'});tests=$results;drift=$drift;populationChanges=$population}|ConvertTo-Json -Depth 7|Set-Content -LiteralPath (Join-Path $RunRoot 'build-qualification.json') -Encoding utf8NoBOM
if(-not$passed){throw "Android build not qualified: $RunRoot"}
Write-Output $RunRoot
