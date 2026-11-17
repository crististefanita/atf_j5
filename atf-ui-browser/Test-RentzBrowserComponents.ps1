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
$uiAar = Join-Path $uiRepository 'ro/cardtable/card-table-ui/0.1.11/card-table-ui-0.1.11.aar'
$provenance = Get-Content -LiteralPath (Join-Path $uiRepository 'provenance.json') -Raw | ConvertFrom-Json
$pinnedUi = @($provenance.files | Where-Object { $_.path -eq 'ro/cardtable/card-table-ui/0.1.11/card-table-ui-0.1.11.aar' })
if ($pinnedUi.Count -ne 1 -or (Get-FileHash -Algorithm SHA256 -LiteralPath $uiAar).Hash -ine $pinnedUi[0].sha256) {
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
$tests = @(
    Join-Path $PSScriptRoot 'tests/RentzBrowserComponentTest.java'
    Join-Path $PSScriptRoot 'tests/ro/cardtable/browser/BrowserSessionRentzContractTest.java'
)
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    $testDependencies = @($classes, $androidUiClasses) -join [IO.Path]::PathSeparator
    & javac --release 17 -encoding UTF-8 -cp $testDependencies -d $testClasses $tests
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component test compilation failed.' }
    $testClasspath = @($testClasses, $classes, $androidUiClasses) -join [IO.Path]::PathSeparator
    & java -cp $testClasspath ro.rentzlab.browser.RentzBrowserComponentTest
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component checks failed.' }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
