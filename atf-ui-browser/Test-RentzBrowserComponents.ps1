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
$null = New-Item -ItemType Directory -Force -Path $javaTemp, $testClasses
$classes = & (Join-Path $PSScriptRoot 'Start-RentzBrowser.ps1') -WorkRoot $root -BuildOnly
if ($LASTEXITCODE -ne 0 -or !$classes) { throw 'Rentz browser adapter build failed.' }
$tests = @(
    Join-Path $PSScriptRoot 'tests/RentzBrowserComponentTest.java'
    Join-Path $PSScriptRoot 'tests/ro/cardtable/browser/BrowserSessionRentzContractTest.java'
)
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = (($previousJavaOptions, "-Djava.io.tmpdir=$javaTemp" | Where-Object { $_ }) -join ' ')
    & javac --release 17 -encoding UTF-8 -cp $classes -d $testClasses $tests
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component test compilation failed.' }
    $testClasspath = @($testClasses, $classes) -join [IO.Path]::PathSeparator
    & java -cp $testClasspath ro.rentzlab.browser.RentzBrowserComponentTest
    if ($LASTEXITCODE -ne 0) { throw 'Rentz browser component checks failed.' }
} finally {
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
}
