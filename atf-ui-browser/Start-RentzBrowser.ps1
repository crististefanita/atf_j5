#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$BrowserRoot,
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
& (Join-Path $BrowserRoot 'Start-CardTableBrowser.ps1') -WorkRoot $WorkRoot -GameRoot $checkout `
    -JavaInputs $sources -MainClass 'ro.rentzlab.browser.RentzBrowser' -Port $Port `
    -ListenAddress $ListenAddress -BuildOnly:$BuildOnly
