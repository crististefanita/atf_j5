param(
    [string]$RunRoot = '',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Maven = 'C:\tools\Maven\apache-maven-3.10.0\bin\mvn.cmd',
    [string]$MavenRepository = ''
)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
if (-not $RunRoot) { $RunRoot = Join-Path 'C:\work\rentz_back\components' ((Get-Date -Format yyyyMMdd-HHmmss)+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)) }
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
if ($RunRoot.Equals($root,[StringComparison]::OrdinalIgnoreCase) -or $RunRoot.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Results belong outside the source project.' }
if (Test-Path -LiteralPath $RunRoot) { throw 'A fresh run directory is required.' }
Import-Module (Join-Path $PSScriptRoot 'AtfMavenCache.psm1') -Force
$repository = Resolve-AtfJ5MavenRepository -Repository $MavenRepository
$stage = Join-Path ([IO.Path]::GetTempPath()) ('rentz-components-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $RunRoot,$stage | Out-Null
$parts = @('pom.xml','lib','atf-test-kit','atf-tests','rentz-lab-app','config','scripts/run-rentz-components.ps1','scripts/AtfMavenCache.psm1')
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
        & $Maven -B -o "-Dmaven.repo.local=$repository" '-Datf.test.heap.max=256m' "-Drentz.report.output=$RunRoot\rentz-rules-extent.html" test *> "$RunRoot\maven.log"
        $exit=$LASTEXITCODE
    } finally { Pop-Location }
} finally { $env:JAVA_HOME=$savedJava; $cacheLease.Dispose() }
$results=@()
foreach ($module in @('atf-test-kit','rentz-lab-app/game-core','atf-tests')) {
    $reports=Join-Path $stage "$module/target/surefire-reports"
    $destination=Join-Path $RunRoot ($module.Replace('/','-'))
    New-Item -ItemType Directory -Path $destination | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue) {
        Copy-Item -LiteralPath $file.FullName -Destination $destination
        [xml]$xml=Get-Content -Raw -LiteralPath $file.FullName
        $results+=[pscustomobject]@{module=$module;name=$xml.testsuite.name;tests=[int]$xml.testsuite.tests;failed=[int]$xml.testsuite.failures;errors=[int]$xml.testsuite.errors;skipped=[int]$xml.testsuite.skipped}
    }
}
$drift=@(foreach($row in $manifest){if((Get-FileHash -LiteralPath (Join-Path $root $row.path) -Algorithm SHA256).Hash -ne $row.sha256){$row.path}})
$afterPaths=@(foreach($part in $parts){
    $origin=Join-Path $root $part
    if(Test-Path -LiteralPath $origin -PathType Leaf){$files=@(Get-Item -LiteralPath $origin)}
    else{$files=@(Get-ChildItem -LiteralPath $origin -Recurse -File | Where-Object {[IO.Path]::GetRelativePath($origin,$_.FullName) -notmatch '(^|[\\/])(target|build|\.gradle|\.git|node_modules)([\\/]|$)'})}
    foreach($file in $files){[IO.Path]::GetRelativePath($root,$file.FullName).Replace('\','/')}
})
$populationChanges=@(Compare-Object @($manifest.path | Sort-Object) @($afterPaths | Sort-Object))
$healthy=$exit -eq 0 -and $results.Count -ge 4 -and @($results | Where-Object {$_.failed -or $_.errors -or $_.skipped -or $_.tests -lt 1}).Count -eq 0 -and $drift.Count -eq 0
$healthy=$healthy -and $populationChanges.Count -eq 0
[ordered]@{status=$(if($healthy){'PASS'}else{'FAIL'});source=$root;stage=$stage;repository=$repository;results=$results;drift=$drift;populationChanges=$populationChanges;scope='Offline Rentz model and ATF integration; reference parity and Android E2E are not qualified.'} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath "$RunRoot\result.json" -Encoding utf8NoBOM
if (-not $healthy) { throw "Component run not qualified; inspect $RunRoot" }
Write-Output $RunRoot
