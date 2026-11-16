#requires -Version 7.0
param(
    [string]$BrowserRoot,
    [Parameter(Mandatory)][string]$WorkRoot,
    [ValidateRange(0,65535)][int]$Port = 8715,
    [string]$ListenAddress = '127.0.0.1',
    [switch]$BuildOnly
)
$checkout = Join-Path $PSScriptRoot '..'
$sources = @(
    Join-Path $PSScriptRoot 'src/main/java'
    Join-Path $checkout 'rentz-lab-app/game-core/src/main/java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/GameSession.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementHand.java'
    Join-Path $checkout 'rentz-lab-app/app/src/main/java/ro/rentzlab/app/achievements/AchievementTactics.java'
)
$main = 'ro.rentzlab.browser.RentzBrowser'
if ($BrowserRoot) {
    & (Join-Path $BrowserRoot 'Start-CardTableBrowser.ps1') -WorkRoot $WorkRoot -GameRoot $checkout `
        -JavaInputs $sources -MainClass $main -Port $Port -ListenAddress $ListenAddress -BuildOnly:$BuildOnly
    return
}
$library = Join-Path $PSScriptRoot 'lib/atf-ui-browser-0.1.0.jar'
$expected = (Get-Content -LiteralPath "$library.sha256" -Raw).Trim()
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $library).Hash -ine $expected) {
    throw 'Browser library checksum mismatch.'
}
$runtime = & java -cp $library ro.cardtable.browser.tool.BrowserBuild $WorkRoot $checkout @sources
if ($LASTEXITCODE -ne 0) { throw 'Browser adapter compilation failed.' }
if ($BuildOnly) { $runtime; return }
& java -cp $runtime $main '-' $ListenAddress $Port
if ($LASTEXITCODE -ne 0) { throw 'Browser server stopped with an error.' }
