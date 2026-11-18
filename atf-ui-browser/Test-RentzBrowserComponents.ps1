#requires -Version 7.0
param([Parameter(Mandatory)][string]$WorkRoot)
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($WorkRoot)
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (($root.Equals($checkout, [StringComparison]::OrdinalIgnoreCase)) -or
        ($root.StartsWith($checkout + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase))) {
    throw 'Use an external Rentz work directory; component outputs do not belong in the source tree.'
}
$null = New-Item -ItemType Directory -Force -Path $root
$javaTemp = Join-Path $root 'java-temp'
$testClasses = Join-Path $root 'rentz-browser-test-classes'
$androidUiClasses = Join-Path $root 'card-table-ui-classes.jar'
$null = New-Item -ItemType Directory -Force -Path $javaTemp, $testClasses
$checkout = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$uiRepository = Join-Path $checkout 'rentz-lab-app/ui-library'
$provenance = Get-Content -LiteralPath (Join-Path $uiRepository 'provenance.json') -Raw | ConvertFrom-Json
if ($provenance.producer -ne 'whist') { throw 'The Android UI timing reference must come from the qualified Whist package.' }
$pinnedUi = @($provenance.files | Where-Object { $_.path -match '^ro/cardtable/card-table-ui/[^/]+/card-table-ui-[^/]+\.aar$' })
if ($pinnedUi.Count -ne 1) { throw 'Expected exactly one versioned Card Table UI AAR in provenance.json.' }
$uiAar = Join-Path $uiRepository $pinnedUi[0].path.Replace('/', [IO.Path]::DirectorySeparatorChar)
if (!(Test-Path -LiteralPath $uiAar) -or (Get-FileHash -Algorithm SHA256 -LiteralPath $uiAar).Hash -ine $pinnedUi[0].sha256) {
    throw 'The Android UI pacing reference does not match the pinned product AAR.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($uiAar)
try {
    $classesEntry = $archive.GetEntry('classes.jar')
    if (!$classesEntry) { throw 'The pinned Android UI AAR has no classes.jar.' }
    $input = $classesEntry.Open()
    $output = [IO.File]::Create($androidUiClasses)
    try { $input.CopyTo($output) }
    finally { $output.Dispose(); $input.Dispose() }
} finally { $archive.Dispose() }
$classes = & (Join-Path $PSScriptRoot 'Start-RentzBrowser.ps1') -WorkRoot $root -BuildOnly
if ($LASTEXITCODE -ne 0 -or !$classes) { throw 'Rentz browser adapter build failed.' }
$sourceCommit = '41d247d50627b66f438af2af6d580cb600ba2a4a'
$sharedRuntime = & (Join-Path $PSScriptRoot 'Verify-CardTableBrowser.ps1') -Version '0.2.13' -SourceCommit $sourceCommit
$sharedTests = Join-Path $PSScriptRoot 'lib/atf-ui-browser-0.2.13-tests.jar'
$sharedRoot = Join-Path $root 'shared-viewport-contract'
$null = New-Item -ItemType Directory -Force -Path $sharedRoot
Push-Location $sharedRoot
try {
    & jar xf $sharedRuntime 'ro/cardtable/browser/web/viewport.mjs'
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the shared viewport runtime.' }
    & jar xf $sharedTests 'viewport-contract.mjs'
    if ($LASTEXITCODE -ne 0) { throw 'Could not extract the shared viewport component test.' }
} finally { Pop-Location }
& node (Join-Path $sharedRoot 'viewport-contract.mjs') (Join-Path $sharedRoot 'ro/cardtable/browser/web/viewport.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Shared browser viewport component checks failed.' }
$tests = @(
    Join-Path $PSScriptRoot 'tests/RentzBrowserComponentTest.java'
    Join-Path $PSScriptRoot 'tests/ro/cardtable/browser/BrowserSessionRentzContractTest.java'
)
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    $testDependencies = @($androidUiClasses, $classes) -join [IO.Path]::PathSeparator
    & javac --release 17 -encoding UTF-8 -cp $testDependencies -d $testClasses $tests
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component test compilation failed.' }
    $testClasspath = @($testClasses, $androidUiClasses, $classes) -join [IO.Path]::PathSeparator
    & java -cp $testClasspath ro.rentzlab.browser.RentzBrowserComponentTest
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component checks failed.' }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
