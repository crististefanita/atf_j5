param(
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$ComponentRun,
    [string]$JavaHome='C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Sdk='C:\tools\whist-android-sdk',
    [ValidateSet('Debug','Release')][string]$Configuration='Debug',
    [switch]$UiSanity,
    [switch]$UiSanityOnly
)
$ErrorActionPreference='Stop'
if($UiSanityOnly-and($UiSanity-or$Configuration-eq'Release')){
    throw 'UiSanityOnly builds only the isolated test package with debug signing.'
}
$buildUi=$UiSanity-or$UiSanityOnly
$root=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $root
$RunRoot=[IO.Path]::GetFullPath($RunRoot)
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $root
if($RunRoot.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)-or$RunRoot-eq$root-or(Test-Path -LiteralPath $RunRoot)){throw 'A fresh external build directory is required.'}
$component=Get-Content -Raw -LiteralPath (Join-Path $ComponentRun 'result.json')|ConvertFrom-Json
if($component.status-ne'PASS'){throw 'Qualified offline component source required.'}
if($component.collection.name-ne'regression'-or-not$component.androidJvm){throw 'Full offline regression including Android JVM is required before assembling an APK.'}
$source=$root
$sourceCommit=''
if($Configuration-eq'Release'-or$UiSanityOnly){
    if(-not$component.sourceCommit){throw 'Release requires regression from a pinned source worktree.'}
    $source=[IO.Path]::GetFullPath($component.source)
    $sourceCommit=Get-AtfReleaseSourceCommit -SourceRoot $source -ProjectRoot $root
    if($sourceCommit-cne$component.sourceCommit){throw 'Release source differs from the qualified commit.'}
}
$reportingLibrary=Get-AtfReportingLibrary -ProjectRoot $source
$componentManifest=Join-Path $ComponentRun 'source-identity.json'
$componentManifestDigest=(Get-FileHash -LiteralPath $componentManifest -Algorithm SHA256).Hash.ToLowerInvariant()
$receiptManifestDigest=([string]$component.sourceManifestSha256).ToLowerInvariant()
if($receiptManifestDigest-notmatch'^[0-9a-f]{64}$'-or$componentManifestDigest-cne$receiptManifestDigest){
    throw 'Component source manifest differs from the receipt.'
}
$androidReceipt=Get-Content -Raw -LiteralPath (Join-Path $component.androidJvm 'result.json')|ConvertFrom-Json
if($androidReceipt.status-ne'PASS'){throw 'Passing Android JVM receipt required.'}
$qualified=@{}
foreach($row in Get-Content -Raw -LiteralPath (Join-Path $component.androidJvm 'source.json')|ConvertFrom-Json){
    $qualified.Add($row.path,$row.sha256)
}
$regressionInputs=@{}
foreach($row in Get-Content -Raw -LiteralPath $componentManifest|ConvertFrom-Json){
    $regressionInputs.Add($row.path,$row.sha256)
}
$lab=Join-Path $source 'rentz-lab-app'
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
$stage=New-AtfWorkspace -Kind android -ProjectRoot $root
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$stage|Set-Content -LiteralPath (Join-Path $RunRoot 'stage.txt') -Encoding utf8NoBOM
$manifest=@(foreach($file in Get-ChildItem -LiteralPath $lab -Recurse -File|Where-Object{[IO.Path]::GetRelativePath($lab,$_.FullName)-notmatch'(^|[\\/])(build|target|\.gradle|\.git)([\\/]|$)'-and$_.Name-ne'local.properties'}){
    $relative=[IO.Path]::GetRelativePath($lab,$file.FullName);$target=Join-Path $stage $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $target)|Out-Null;Copy-Item -LiteralPath $file.FullName -Destination $target
    $hash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash
    if($hash-ne(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash){throw 'Source changed during Android snapshot'}
    $path=$relative.Replace('\','/')
    if($qualified[$path]-ine$hash-or$regressionInputs["rentz-lab-app/$path"]-ine$hash){
        throw "Android build input differs from qualified source: $path"
    }
    [pscustomobject]@{path=$path;sha256=$hash}
})
if($manifest.Count-ne$qualified.Count){throw 'Android build input population differs from qualified source.'}
ConvertTo-Json -InputObject $manifest -Depth 4|Set-Content -LiteralPath (Join-Path $RunRoot 'source-identity.json') -Encoding utf8NoBOM
('sdk.dir='+$Sdk.Replace('\','/'))|Set-Content -LiteralPath (Join-Path $stage 'local.properties') -Encoding ascii
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
$cacheLease=Enter-AtfJ5CacheLease -Repository $component.repository
$savedJava=$env:JAVA_HOME;$savedOptions=$env:JAVA_TOOL_OPTIONS;$savedStore=$env:RENTZ_STORE_FILE;$savedPassword=$env:RENTZ_STORE_PASSWORD;$exit=1
try{
    $env:JAVA_HOME=$JavaHome
    $runtime=New-Item -ItemType Directory -Path (Join-Path $stage 'runtime')
    $env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$($runtime.FullName)"
    if($Configuration-eq'Release'){
        $signing=Get-AtfInfrastructurePath -Area signing -ProjectRoot $root
        $identity=Get-Content -Raw -LiteralPath (Join-Path $signing 'identity.json')|ConvertFrom-Json
        $secure=(Get-Content -Raw -LiteralPath $identity.credential).Trim()|ConvertTo-SecureString
        $env:RENTZ_STORE_PASSWORD=[Net.NetworkCredential]::new('rentz',$secure).Password
        $env:RENTZ_STORE_FILE=$identity.store
    }
    $tasks=@(':game-core:test',':app:testDebugUnitTest')
    if(-not$UiSanityOnly){$tasks+=(':app:assemble'+$Configuration)}
    if($buildUi){$tasks+=@("-Prentz.uiEvidence=$RunRoot/assertions",':app:lintDebug',':app:assembleUiSanity')}
    & (Join-Path $stage 'gradlew.bat') -p $stage --offline --no-daemon ("-Prentz.mavenRepository="+$component.repository.Replace('\','/')) @tasks *> (Join-Path $RunRoot 'build.log')
    $exit=$LASTEXITCODE
}finally{$env:JAVA_HOME=$savedJava;$env:JAVA_TOOL_OPTIONS=$savedOptions;$env:RENTZ_STORE_FILE=$savedStore;$env:RENTZ_STORE_PASSWORD=$savedPassword;$secure=$null;$cacheLease.Dispose()}
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
if($sourceCommit){
    try{if((Get-AtfReleaseSourceCommit -SourceRoot $source -ProjectRoot $root)-cne$sourceCommit){$drift+='Release source commit changed'}}
    catch{$drift+=$_.Exception.Message}
}
$passed=$exit-eq0-and$results.Count-ge2-and@($results|Where-Object{$_.failures-or$_.errors-or$_.skipped-or$_.tests-lt1}).Count-eq0-and@($results|Where-Object{$_.module-eq'app'}).Count-gt0-and$drift.Count-eq0-and$population.Count-eq0
$apk=Join-Path $RunRoot 'rentz-lab-debug.apk';$hash='';$certificate=@();$version=$null;$debuggable=$null;$applicationId='';$apkBytes=0
$uiApk='';$uiApkHash='';$uiApplicationId='';$uiAuditedCases=0;$uiCertificate=@();$uiApkBytes=0
if($passed){
    if(-not$UiSanityOnly){
    $variant=$Configuration.ToLowerInvariant()
    $version=(Get-Content -Raw -LiteralPath (Join-Path $stage "app/build/outputs/apk/$variant/output-metadata.json")|ConvertFrom-Json).elements[0]
    if($Configuration-eq'Release'){$apk=Join-Path $RunRoot ('RentzLab-'+$version.versionName+'.apk')}
    Copy-Item -LiteralPath (Join-Path $stage "app/build/outputs/apk/$variant/app-$variant.apk") -Destination $apk
    $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    & (Join-Path $Sdk 'build-tools/36.0.0/apksigner.bat') verify --verbose --print-certs $apk *> (Join-Path $RunRoot 'apk-signature.txt');$signed=$LASTEXITCODE-eq0
    & (Join-Path $Sdk 'build-tools/36.0.0/aapt.exe') dump badging $apk *> (Join-Path $RunRoot 'apk-badging.txt');$identified=$LASTEXITCODE-eq0
    $productPackage=[regex]::Match((Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'apk-badging.txt')),"(?m)^package: name='([^']+)'")
    if($productPackage.Success){$applicationId=$productPackage.Groups[1].Value}else{$identified=$false}
    $certificate=@(Get-Content -LiteralPath (Join-Path $RunRoot 'apk-signature.txt')|Where-Object{$_-match'certificate SHA-256 digest:'})
    $passed=$passed-and$signed-and$identified-and$applicationId-eq'ro.rentzlab.app'-and$certificate.Count-eq1
    & (Join-Path $Sdk 'build-tools/36.0.0/zipalign.exe') -c -P 16 -v 4 $apk *> (Join-Path $RunRoot 'apk-alignment.txt')
    $passed=$passed-and$LASTEXITCODE-eq0
    $debuggable=[bool](@(Get-Content -LiteralPath (Join-Path $RunRoot 'apk-badging.txt')|Where-Object{$_-eq'application-debuggable'}).Count)
    $apkBytes=(Get-Item -LiteralPath $apk).Length
    if($Configuration-eq'Release'-and$certificate.Count-eq1){
        $actual=($certificate[0]-split':\s*',2)[1].Trim().ToLowerInvariant()
        $passed=$passed-and-not$debuggable-and$actual-eq$identity.certificateSha256
    }elseif($Configuration-eq'Release'){$passed=$false}
    }
    if($buildUi){
        $uiMetadata=Get-Content -Raw -LiteralPath (Join-Path $stage 'app/build/outputs/apk/uiSanity/output-metadata.json')|ConvertFrom-Json
        if(-not$uiMetadata.elements.Count){throw 'Isolated UI-sanity APK metadata is missing.'}
        if($UiSanityOnly){
            $uiVersion=[string]$uiMetadata.elements[0].versionName
            if(-not$uiVersion.EndsWith('-ui-sanity',[StringComparison]::Ordinal)){throw 'Expected isolated test version suffix'}
            $version=[pscustomobject]@{versionName=$uiVersion.Substring(0,$uiVersion.Length-10);versionCode=$uiMetadata.elements[0].versionCode}
        }
        $uiSource=Join-Path $stage ('app/build/outputs/apk/uiSanity/'+$uiMetadata.elements[0].outputFile)
        $uiApk=Join-Path $RunRoot 'rentz-lab-ui-sanity.apk';Copy-Item -LiteralPath $uiSource -Destination $uiApk
        $uiApkHash=(Get-FileHash -LiteralPath $uiApk -Algorithm SHA256).Hash
        & (Join-Path $Sdk 'build-tools/36.0.0/apksigner.bat') verify --verbose --print-certs $uiApk *> (Join-Path $RunRoot 'ui-apk-signature.txt');$uiSigned=$LASTEXITCODE-eq0
        & (Join-Path $Sdk 'build-tools/36.0.0/aapt.exe') dump badging $uiApk *> (Join-Path $RunRoot 'ui-apk-badging.txt');$uiIdentified=$LASTEXITCODE-eq0
        $uiPackage=[regex]::Match((Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'ui-apk-badging.txt')),"(?m)^package: name='([^']+)'")
        if($uiPackage.Success){$uiApplicationId=$uiPackage.Groups[1].Value}else{$uiIdentified=$false}
        $uiCertificate=@(Get-Content -LiteralPath (Join-Path $RunRoot 'ui-apk-signature.txt')|Where-Object{$_-match'certificate SHA-256 digest:'})
        & (Join-Path $Sdk 'build-tools/36.0.0/zipalign.exe') -c -P 16 -v 4 $uiApk *> (Join-Path $RunRoot 'ui-apk-alignment.txt');$uiAligned=$LASTEXITCODE-eq0
        $uiDebuggable=[bool](@(Get-Content -LiteralPath (Join-Path $RunRoot 'ui-apk-badging.txt')|Where-Object{$_-eq'application-debuggable'}).Count)
        $auditDirectory=Join-Path $RunRoot 'assertions'
        if(Test-Path -LiteralPath $auditDirectory -PathType Container){
            foreach($auditFile in Get-ChildItem -LiteralPath $auditDirectory -Filter '*.json' -File){
                $audit=Get-Content -Raw -LiteralPath $auditFile.FullName|ConvertFrom-Json
                if($audit.status-ne'PASS'-or-not$audit.checks-or@($audit.checks|Where-Object{-not$_.passed}).Count){$passed=$false;throw "UI component audit did not pass: $($auditFile.Name)"}
                $uiAuditedCases+=$audit.checks.Count
            }
        }
        $passed=$passed-and$uiSigned-and$uiIdentified-and$uiAligned-and$uiApplicationId-eq'ro.rentzlab.app.uisanity'-and$uiDebuggable-and$uiCertificate.Count-eq1-and$uiAuditedCases-gt0
        $uiApkBytes=(Get-Item -LiteralPath $uiApk).Length
    }
}
if(-not$UiSanityOnly){
[ordered]@{
    status=$(if($passed){'PASS'}else{'FAIL'})
    uiContract=$(if(Test-Path -LiteralPath (Join-Path $stage 'app/src/main/java/ro/rentzlab/app/ui/table/RentzTableContent.java')){'card-table-ui-v1'}else{'prototype-v1'})
    configuration=$Configuration;debuggable=$debuggable;stage=$stage;sourceCommit=$sourceCommit;componentRun=$ComponentRun
    qualifiedCoreMatches=$true;builderSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    sourceManifest=(Join-Path $RunRoot 'source-identity.json');sourceManifestSha256=(Get-FileHash -LiteralPath (Join-Path $RunRoot 'source-identity.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    apkFile=[IO.Path]::GetFileName($apk);apk=$apk;apkSha256=$hash;apkBytes=$apkBytes;versionName=$version.versionName;versionCode=$version.versionCode
    certificate=$certificate;applicationId=$applicationId
    productApk=$apk;productApkSha256=$hash;productApkBytes=$apkBytes;productVersionName=$version.versionName
    productApplicationId=$applicationId;productDebuggable=$debuggable;productCertificate=$certificate
    uiSanity=[bool]$UiSanity;uiSanityApk=$uiApk;uiSanityApkSha256=$uiApkHash;uiSanityApkBytes=$uiApkBytes
    uiSanityApplicationId=$uiApplicationId;uiSanityCertificate=$uiCertificate;newAuditedCases=$uiAuditedCases
    reportingLibrary=$reportingLibrary.name;reportingLibrarySha256=$reportingLibrary.sha256
    signing=$(if($Configuration-eq'Release'){'Dedicated Rentz release identity; external DPAPI credential'}else{'Local Android debug certificate; not an original-app release'})
    tests=$results;drift=$drift;populationChanges=$population
}|ConvertTo-Json -Depth 7|Set-Content -LiteralPath (Join-Path $RunRoot 'build-qualification.json') -Encoding utf8NoBOM
}
if($buildUi){
    [ordered]@{
        status=$(if($passed){'PASS'}else{'FAIL'});uiSanity=$true;sourceCommit=$sourceCommit
        applicationId=$uiApplicationId;apk=$uiApk;apkSha256=$uiApkHash;apkBytes=$uiApkBytes;versionName=$version.versionName
        newAuditedCases=$uiAuditedCases;builderSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
        sourceManifest=(Join-Path $RunRoot 'source-identity.json');sourceManifestSha256=(Get-FileHash -LiteralPath (Join-Path $RunRoot 'source-identity.json') -Algorithm SHA256).Hash.ToLowerInvariant()
        componentRun=$ComponentRun;reportingLibrary=$reportingLibrary.name
        reportingLibrarySha256=$reportingLibrary.sha256
        certificate=$uiCertificate
    }|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $RunRoot 'ui-sanity-build-qualification.json') -Encoding utf8NoBOM
}
if(-not$passed){throw "Android build not qualified: $RunRoot"}
Write-Output $RunRoot
