param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
Import-Module (Join-Path $PSScriptRoot '../../rentz-reference/tools/RentzUiOracle.psm1') -Force
if(Test-Path -LiteralPath $RunRoot){throw 'Fresh selector component output required.'}
New-Item -ItemType Directory -Path $RunRoot|Out-Null
$checks=[Collections.Generic.List[object]]::new()
function Check([string]$Name,$Expected,$Actual){
    $pass=[string]$Expected-ceq[string]$Actual
    $checks.Add([pscustomobject]@{name=$Name;expected=[string]$Expected;actual=[string]$Actual;passed=$pass})
    if(-not$pass){throw "Selector assertion failed: $Name"}
}
$failure=''
try{
    [xml]$picker='<hierarchy><node class="android.widget.ListView"><node text="Choice" content-desc="" class="android.widget.CheckedTextView" enabled="true" clickable="false" bounds="[10,20][50,60]"/></node></hierarchy>'
    $target=Get-RentzTapTarget -Nodes @($picker.SelectNodes('//node')) -Label 'Choice'
    Check 'Native AdapterView item is actionable even when its child is not clickable' '[10,20][50,60]' $target.bounds
    $picker.SelectSingleNode('//node/node').SetAttribute('enabled','false')
    Check 'Disabled list item is rejected' $true ($null-eq(Get-RentzTapTarget -Nodes @($picker.SelectNodes('//node')) -Label 'Choice'))
    [xml]$passive='<hierarchy><node text="Choice" content-desc="" class="android.widget.TextView" enabled="true" clickable="false" bounds="[10,20][50,60]"/></hierarchy>'
    Check 'Passive label outside an AdapterView is not a control' $true ($null-eq(Get-RentzTapTarget -Nodes @($passive.SelectNodes('//node')) -Label 'Choice'))
    [xml]$ambiguous='<hierarchy><node text="Choice" content-desc="" class="android.widget.Button" enabled="true" clickable="true"/><node text="Choice" content-desc="" class="android.widget.Button" enabled="true" clickable="true"/></hierarchy>'
    $rejected=$false;try{$null=Get-RentzTapTarget -Nodes @($ambiguous.SelectNodes('//node')) -Label 'Choice'}catch{$rejected=$_.Exception.Message-like'Ambiguous native control:*'}
    Check 'Two matching targets fail rather than choosing one' $true $rejected

    function Actor([long]$Revision,[int]$Hand,[string]$Phase,[int]$Turn,[string]$Contract='RENTZ',$Points=@(10,5,0),$Finish=@(0,2,1)){
        @{revision=$Revision;screen='GAME';busy=$false;view=@{handNumber=$Hand;phase=$Phase;turn=$Turn;contract=$Contract;players=3;dealPoints=$Points;finishOrder=$Finish}}
    }
    $settled=Actor 10 1 'DEAL_END' 0
    $calls=[Collections.Generic.List[string]]::new()
    $next=Start-RentzNextDeal -Completed $settled -Advance {$calls.Add('next')} -ReadActor {
        param($after)
        $calls.Add("read:$after")
        if($after-eq10){Actor 11 2 'CHOOSE' 1}else{Actor 12 2 'CHOOSE' 0}
    } -ChooseHuman {
        param($chooser)
        $calls.Add("choose:$($chooser.revision)")
        Actor 13 2 'PLAY' 0 'WHIST'
    }
    Check 'Advance the UI once and process each chooser at a fresh revision' 'next,read:10,read:11,choose:12' ($calls -join ',')
    Check 'Next deal enters play with the expected hand and contract' '2:WHIST' "$($next.view.handNumber):$($next.view.contract)"
    $calls.Clear()
    foreach($case in @(
        @{name='Premature next hand cannot tap the UI'; completed=(Actor 10 1 'PLAY' 0); frame=$null; error='ANDROID-NEXT-DEAL: previous hand is not settled.'; actions=0},
        @{name='Stale next-hand state fails without tapping again'; completed=$settled; frame=(Actor 10 2 'PLAY' 0); error='ANDROID-NEXT-DEAL: stale or busy actor observation.'; actions=1},
        @{name='New revision from the previous hand is rejected'; completed=$settled; frame=(Actor 11 1 'CHOOSE' 1); error='ANDROID-NEXT-DEAL: wrong hand number.'; actions=1},
        @{name='Premature score screen cannot substitute for play'; completed=$settled; frame=(Actor 11 2 'DEAL_END' 0); error='ANDROID-NEXT-DEAL: expected CHOOSE or PLAY.'; actions=1}
    )){
        $failureText=''
        try{
            $null=Start-RentzNextDeal -Completed $case.completed -Advance {$calls.Add('next')} -ReadActor {$case.frame} -ChooseHuman {throw 'Unexpected choice'}
        }catch{$failureText=$_.Exception.Message}
        Check $case.name "$($case.error) [$($case.actions)]" "$failureText [$($calls.Count)]"
        $calls.Clear()
    }
    $stuck=''
    try{
        $null=Start-RentzNextDeal -Completed $settled -Advance {$calls.Add('next')} -ReadActor {
            param($after)
            Actor ($after+1) 2 'CHOOSE' 1
        } -ChooseHuman {throw 'Unexpected choice'}
    }catch{$stuck=$_.Exception.Message}
    Check 'An unresolved chooser has a bounded observation budget and one UI tap' 'ANDROID-NEXT-DEAL: chooser did not resolve. [1]' "$stuck [$($calls.Count)]"
    $checksForScore=@(Get-RentzCompletedDealChecks -Actor $settled -Hand 1 -Contract 'RENTZ')
    Check 'Settlement verifies phase, hand, contract, score and Rentz order' 5 @($checksForScore|Where-Object passed).Count
    $invalid=Actor 11 2 'PLAY' 1 'WHIST' @(0) @()
    $checksForScore=@(Get-RentzCompletedDealChecks -Actor $invalid -Hand 1 -Contract 'RENTZ')
    Check 'Premature or misattributed scores fail every settlement comparison' 5 @($checksForScore|Where-Object { !$_.passed }).Count

    foreach($players in @(3,5,6)){
        $order=@(($players-1)..0)
        $score=Actor 20 1 'DEAL_END' 0 'RENTZ' @(1..$players) $order
        $score.view.players=$players
        Check "A distinct finish for each of $players places is accepted" 5 @(Get-RentzCompletedDealChecks $score 1 'RENTZ' | Where-Object passed).Count
        $score.view.finishOrder=@($order)
        $score.view.finishOrder[$players-1]=$players-1
        Check "A duplicate finisher cannot stand in for seat zero at $players places" 1 @(Get-RentzCompletedDealChecks $score 1 'RENTZ' | Where-Object {!$_.passed}).Count
        $score.view.finishOrder[$players-1]=$players
        Check "A foreign seat is refused at $players places" 1 @(Get-RentzCompletedDealChecks $score 1 'RENTZ' | Where-Object {!$_.passed}).Count

        $names=@(0..($players-1)|ForEach-Object {"Player$_"})
        $nodes=@(0..($players-1)|ForEach-Object {
            $left=10+$_*100
            [pscustomobject]@{description=$names[$_]+'. Points: 0. Total: 0';text='';bounds="[$left,80][$($left+80),140]"}
        })
        $turn=$players-1
        $left=10+$turn*100
        $badge=[pscustomobject]@{description='';text='CHOOSING';bounds="[$left,50][$($left+80),75]"}
        $frames=@(Get-RentzSeatFrames -Nodes ($nodes+$badge) -Names $names)
        Check "Each player has a measurable frame at $players places" $players $frames.Count
        Check "The action is above the actor at $players places" 3 @(Get-RentzTurnBadgeChecks -Nodes ($nodes+$badge) -Frames $frames -Turn $turn -Label 'CHOOSING' | Where-Object passed).Count
        $badge.text='PASSING'
        Check "A forced pass keeps the same actor at $players places" 3 @(Get-RentzTurnBadgeChecks -Nodes ($nodes+$badge) -Frames $frames -Turn $turn -Label 'PASSING' | Where-Object passed).Count
        $badge.bounds='[10,50][90,75]'
        Check "A badge above another player fails at $players places" 1 @(Get-RentzTurnBadgeChecks -Nodes ($nodes+$badge) -Frames $frames -Turn $turn -Label 'PASSING' | Where-Object {!$_.passed}).Count
        $badBounds=[pscustomobject]@{description=$nodes[$turn].description;text='';bounds=$nodes[0].bounds}
        $refusal=''
        try{$null=Get-RentzSeatFrames -Nodes ($nodes[0..($turn-1)]+$badBounds) -Names $names}catch{$refusal=$_.Exception.Message}
        Check "Overlapping seat frames fail at $players places" "Player frames intersect: Player0 and Player$turn" $refusal
    }

    $history=[Collections.Generic.HashSet[int]]::new()
    $contracts=@('Rentz','Tricks','Whist')
    $page=@(Get-RentzHistoryPageChecks -Labels @('Score history',"1 · Rentz"+[Environment]::NewLine+'scores','2 · Tricks ×2') -Contracts $contracts -Seen $history)
    Check 'History parses a score row and a doubled contract without making another gesture' '3:2' "$(@($page|Where-Object passed).Count):$($history.Count)"
    $page=@(Get-RentzHistoryPageChecks -Labels @('2 · Tricks','3 · Whist') -Contracts $contracts -Seen $history)
    Check 'Overlapping history pages count each completed hand once' '3:3' "$(@($page|Where-Object passed).Count):$($history.Count)"
    $history.Clear()
    $page=@(Get-RentzHistoryPageChecks -Labels @('2 · Rentz') -Contracts $contracts -Seen $history)
    Check 'A mismatched history contract cannot advance the coverage count' '1:0' "$(@($page|Where-Object {!$_.passed}).Count):$($history.Count)"
    $page=@(Get-RentzHistoryPageChecks -Labels @('Score history','') -Contracts $contracts -Seen $history)
    Check 'An empty history viewport fails even when its heading is present' '1:0' "$(@($page|Where-Object {!$_.passed}).Count):$($history.Count)"
    $refusal=''
    try{$null=Get-RentzHistoryPageChecks -Labels @('4 · Whist') -Contracts $contracts -Seen $history}catch{$refusal=$_.Exception.Message}
    Check 'A row outside the completed history is refused' 'Unexpected confirmed hand 4' $refusal
}catch{$failure=$_.Exception.ToString()}
[ordered]@{class='atf.rentz.android.NativeUiOracle';method='selectorsDealsAndHistory';status=$(if($failure){'FAIL'}else{'PASS'});failure=$failure;checks=$checks.ToArray()}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $RunRoot 'assertions.json') -Encoding utf8NoBOM
if($failure){throw $failure}
