#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$Version,
    [Parameter(Mandatory)][string]$SourceCommit,
    [string]$LibraryRoot = (Join-Path $PSScriptRoot 'lib')
)
$ErrorActionPreference = 'Stop'
if ($Version -cnotmatch '^0\.(0|[1-9][0-9]*)(?:\.(0|[1-9][0-9]*))?$' -or $SourceCommit -cnotmatch '^[a-f0-9]{40}$') {
    throw 'Invalid Card Table Browser pin.'
}
$receipt = Get-Content -LiteralPath (Join-Path $LibraryRoot 'browser-build.json') -Raw | ConvertFrom-Json -AsHashtable
if ($receipt.schemaVersion -ne 1 -or $receipt.version -cne $Version -or $receipt.sourceCommit -cne $SourceCommit) {
    throw 'Card Table Browser version or source commit differs from the approved pin.'
}
$inputs = $receipt.inputs
$encoded = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject @($inputs) -Depth 4 -Compress))
$inputHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($encoded)).ToLowerInvariant()
if (!$inputs.Count -or $receipt.inputSha256 -cne $inputHash) { throw 'Card Table Browser source inventory differs.' }

$runtime = "atf-ui-browser-$Version.jar"
$tests = "atf-ui-browser-$Version-tests.jar"
$expected = @($runtime, "$runtime.sha256", $tests, "$tests.sha256")
if ($receipt.files.Count -ne $expected.Count) { throw 'Unexpected Card Table Browser file count.' }
foreach ($name in $expected) {
    $rows = @($receipt.files | Where-Object path -CEQ $name)
    $item = Get-Item -LiteralPath (Join-Path $LibraryRoot $name)
    if ($rows.Count -ne 1 -or $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or
            $item.Length -ne $rows[0].bytes -or (Get-FileHash -LiteralPath $item.FullName).Hash.ToLowerInvariant() -cne $rows[0].sha256) {
        throw "Card Table Browser artifact differs: $name"
    }
}
foreach ($jar in $runtime, $tests) {
    $expectedHash = ($receipt.files | Where-Object path -CEQ $jar).sha256
    if ((Get-Content -LiteralPath (Join-Path $LibraryRoot "$jar.sha256") -Raw).Trim() -cne $expectedHash) {
        throw "Card Table Browser checksum does not identify $jar."
    }
}
Join-Path $LibraryRoot $runtime
