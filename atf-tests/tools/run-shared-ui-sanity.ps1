param([Parameter(Mandatory)][string]$BuildQualification,[Parameter(Mandatory)][string]$RunRoot,
    [switch]$SeatExtremes,[switch]$HistoryOnly,[switch]$HistoryMatrix,[switch]$HistoryDetails,[switch]$HelpAwardsOnly,[switch]$CompactScores,[switch]$ConsecutiveSubgames,[string]$ComponentRun,[string]$Java='C:\tools\Java21\jdk-21.0.12.1+1\bin\java.exe')
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $project 'rentz-reference/tools/RentzAndroid.psm1') -Force
Assert-AtfInfrastructure -ProjectRoot $project
$reportingLibrary=Get-AtfReportingLibrary -ProjectRoot $project
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
if(Test-Path -LiteralPath $RunRoot){throw 'A fresh sanity output directory is required.'}
$build=Get-Content -LiteralPath $BuildQualification -Raw|ConvertFrom-Json
if($build.status-ne'PASS'-or$build.applicationId-ne'ro.rentzlab.app.uisanity'-or$build.newAuditedCases-lt1){throw 'Qualified isolated UI build required.'}
if((Get-FileHash -LiteralPath $build.apk -Algorithm SHA256).Hash-ne$build.apkSha256){throw 'APK changed after qualification.'}
$fixture=$null
if(($HistoryMatrix-or$HistoryDetails)-and-not$HistoryOnly){throw 'History matrix and details require HistoryOnly.'}
if($HelpAwardsOnly-and($HistoryOnly-or$SeatExtremes)){throw 'Choose a single native sanity scope.'}
if($CompactScores-and($HelpAwardsOnly-or$HistoryOnly-or$SeatExtremes)){throw 'Compact scores continue the one-hand sanity.'}
if($ConsecutiveSubgames-and($HelpAwardsOnly-or$HistoryOnly-or$SeatExtremes-or$CompactScores)){throw 'Consecutive subgames are a standalone native sanity scope.'}
if($HistoryOnly){
    if($SeatExtremes){throw 'Choose a single native sanity scope.'}
    Assert-AtfArtifactPath -Path $build.historyFixture -ProjectRoot $project
    if((Get-FileHash -LiteralPath $build.historyFixture).Hash-ne$build.historyFixtureSha256){throw 'History fixture identity changed after qualification.'}
    $fixture=Get-Content -LiteralPath $build.historyFixture -Raw|ConvertFrom-Json
    $journal=Join-Path (Split-Path -Parent $build.historyFixture) 'session.json'
    if($fixture.kind-ne'confirmed-rentz-history'-or$fixture.players-ne6-or$fixture.completedHands-ne48-or
        (Get-FileHash -LiteralPath $journal).Hash-ne$fixture.journalSha256){throw 'Qualified 48-hand Rentz journal required.'}
    if($HistoryDetails-and$fixture.last.finishOrder.Count-ne$fixture.players){throw 'Qualified Rentz finishing order required.'}
}
$developmentLedger=Join-Path (Get-AtfDataRoot -ProjectRoot $project) 'learning/exposure.json'
$sharedDevelopmentLedger=$developmentLedger
$developmentExposures=[Collections.Generic.List[object]]::new()
if(-not $HelpAwardsOnly){
    if(-not $ComponentRun){$ComponentRun=$build.componentRun}
    if(-not $ComponentRun){throw 'A qualified ComponentRun is required for native development exposure.'}
    $component=Get-Content -LiteralPath (Join-Path $ComponentRun 'result.json') -Raw|ConvertFrom-Json
    if($component.status -ne 'PASS' -or -not $component.nativeRuntime){throw 'A qualified native adapter is required.'}
    Import-Module (Join-Path $project 'scripts/RuntimeQualification.psm1') -Force
    Import-Module (Join-Path $project 'scripts/AtfMavenCache.psm1') -Force
    Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage|Out-Null
    Assert-AtfArtifactPath -Path $developmentLedger -ProjectRoot $project
}
function Invoke-DevelopmentExposure([string[]]$Arguments){
    $cacheLease=Enter-AtfJ5CacheLease -Repository $component.repository
    try{
        $runtime=Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage
        & $Java -Xmx128m -cp $runtime.classpath 'atf.rentz.android.RentzNativeFlow' @Arguments|Out-Null
        if($LASTEXITCODE -ne 0){throw "Native exposure command failed: $($Arguments[0])"}
        Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage|Out-Null
    }finally{$cacheLease.Dispose()}
}
function Start-DevelopmentExposure {
    $token=[guid]::NewGuid().ToString('N')
    Invoke-DevelopmentExposure @('begin-exposure',$developmentLedger,$token)
    return $token
}
function Complete-DevelopmentExposure([string]$Journal,[string]$Token){
    $sha256=(Get-FileHash -LiteralPath $Journal).Hash
    Invoke-DevelopmentExposure @('complete-exposure',$Journal,$developmentLedger,$Token)
    if((Get-FileHash -LiteralPath $Journal).Hash -ne $sha256){throw 'Native exposure journal changed during registration.'}
    $developmentExposures.Add([ordered]@{journal=[IO.Path]::GetFileName($Journal);sha256=$sha256;token=$Token})
}
function Invoke-DevelopmentAudit([string]$Journal,[string]$ActorView){
    $cacheLease=Enter-AtfJ5CacheLease -Repository $component.repository
    try{
        $runtime=Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage
        $output=@(& $Java -Xmx128m -cp $runtime.classpath 'atf.rentz.android.RentzNativeFlow' 'audit' $Journal $ActorView)
        if($LASTEXITCODE -ne 0){throw 'Independent native-flow replay failed.'}
        Assert-RentzQualifiedRuntime -Descriptor $component.nativeRuntime -DescriptorSha256 $component.nativeRuntimeSha256 -MainClass 'atf.rentz.android.RentzNativeFlow' -Stage $component.stage|Out-Null
        return (($output -join "`n") | ConvertFrom-Json)
    }finally{$cacheLease.Dispose()}
}
$raw=New-AtfWorkspace -Kind android -ProjectRoot $project
New-Item -ItemType Directory -Path $RunRoot|Out-Null
if(-not $HelpAwardsOnly){
    $developmentLedger=Join-Path $RunRoot 'learning-exposure.json'
    $sharedDevelopmentLedgerSha256Before=(Get-FileHash -LiteralPath $sharedDevelopmentLedger -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $sharedDevelopmentLedger -Destination $developmentLedger
    Assert-AtfArtifactPath -Path $developmentLedger -ProjectRoot $project
    $copiedLedgerHash=(Get-FileHash -LiteralPath $developmentLedger -Algorithm SHA256).Hash.ToLowerInvariant()
    $sharedLedgerAfterCopy=(Get-FileHash -LiteralPath $sharedDevelopmentLedger -Algorithm SHA256).Hash.ToLowerInvariant()
    if($copiedLedgerHash-ne$sharedDevelopmentLedgerSha256Before-or$sharedLedgerAfterCopy-ne$sharedDevelopmentLedgerSha256Before){throw 'Shared learning ledger changed while the isolated test copy was created.'}
    $isolatedDevelopmentLedgerSha256Before=$copiedLedgerHash
}
$leasePath='C:\work\whist_back\android-ui-lease.json';$leaseId=[guid]::NewGuid().ToString('N')
$remoteJournal="/data/local/tmp/rentz-history-$leaseId.json";$journalPushed=$false
$owned=$false;$installed=$false;$released=$false;$failure='';$extentPath='';$ordinal=0;$initialActivity=''
$points=[Collections.Generic.List[object]]::new();$actions=[Collections.Generic.List[object]]::new();$cleanup=[Collections.Generic.List[string]]::new();$completedGames=[Collections.Generic.List[object]]::new()
$initialSize='';$initialDensity='';$initialFont='';$finalSize='';$finalDensity='';$finalFont='';$packagesBefore='';$packagesAfter='';$api=''
$wasAsleep=$false
$displayChanged=$false;$displayProfile='';$logicalProfile='';$restoreSize='';$restoreDensity='';$restoreFont=''
function Adb([string[]]$Command,[string]$Name,[int[]]$Allowed=@(0)){
    $script:ordinal++;Invoke-RentzAdb -RunRoot $raw -Arguments $Command -Label ('{0:d4}-{1}'-f$script:ordinal,$Name) -AllowedExitCodes $Allowed
}
function Ui {
    $remote='/sdcard/rentz-ui-'+$leaseId+'.xml'
    for($attempt=0;$attempt-lt3;$attempt++){
        $dump=Adb @('shell','uiautomator','dump',$remote) 'dump'
        if(($dump.Output+$dump.Error)-match[regex]::Escape($remote)-and($dump.Output+$dump.Error)-notmatch'(?i)ERROR|Exception'){
            [xml]$doc=(Adb @('exec-out','cat',$remote) 'hierarchy').Output
            return @($doc.SelectNodes('//node'))
        }
        Start-Sleep -Milliseconds 300
    }
    throw 'Fresh native hierarchy unavailable after the three observation attempts.'
}
function Bounds($node){
    $m=[regex]::Match([string]$node.bounds,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if(-not$m.Success){throw 'Invalid native element bounds.'}
    return @([int]$m.Groups[1].Value,[int]$m.Groups[2].Value,[int]$m.Groups[3].Value,[int]$m.Groups[4].Value)
}
function Tap([string]$Label,[string]$Class=''){
    $deadline=[datetime]::UtcNow.AddSeconds(30)
    do{
        $node=Get-RentzTapTarget -Nodes @(Ui) -Label $Label -Class $Class
        if($null-ne$node){
            TapNode $node $Label
            return
        }
        Start-Sleep -Milliseconds 250
    }while([datetime]::UtcNow-lt$deadline)
    throw "Native control unavailable: $Label"
}
function TapNode($Node,[string]$Label){
    $box=Bounds $Node;$watch=[Diagnostics.Stopwatch]::StartNew()
    Adb @('shell','input','tap',[string][int](($box[0]+$box[2])/2),[string][int](($box[1]+$box[3])/2)) 'tap'|Out-Null;$watch.Stop()
    $actions.Add([pscustomobject]@{operation='Tap Android control';control=$Label;bounds=$Node.bounds;elapsedMillis=$watch.ElapsedMilliseconds})
}
function State([long]$After=-1,[string]$Screen='GAME'){
    $deadline=[datetime]::UtcNow.AddSeconds(25)
    do{
        $read=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/actor-view.json') 'actor' -Allowed @(0,1)
        if($read.ExitCode-eq0){
            try{$state=$read.Output|ConvertFrom-Json;if($state.revision-gt$After-and$state.screen-eq$Screen-and-not$state.busy){return $state}}catch{}
        }
        Start-Sleep -Milliseconds 200
    }while([datetime]::UtcNow-lt$deadline)
    throw 'Current actor checkpoint did not reach the expected revision and route.'
}
function Check([string]$Name,$Expected,$Actual){[pscustomobject]@{name=$Name;expected=[string]$Expected;actual=[string]$Actual;passed=([string]$Expected-ceq[string]$Actual)}}
function Observe([string]$Title,[scriptblock]$Verify){
    $deadline=[datetime]::UtcNow.AddSeconds(25);$attempt=0;$lastFailure=$null
    do{
        $attempt++;$name='checkpoint-{0:d2}-attempt-{1:d2}'-f($points.Count+1),$attempt
        $watch=[Diagnostics.Stopwatch]::StartNew()
        try{$nodes=@(Save-RentzObservation -RunRoot $raw -Name $name);$lastFailure=$null;break}
        catch{if($_.Exception.Message-notlike'Fresh hierarchy was not confirmed*'){throw};$lastFailure=$_.Exception;Start-Sleep -Milliseconds 250}
        finally{$watch.Stop()}
    }while([datetime]::UtcNow-lt$deadline)
    if($lastFailure){throw $lastFailure}
    $checks=@(& $Verify $nodes)
    $capturePixels=''
    if($displayProfile){
        $png=[IO.File]::ReadAllBytes((Join-Path $raw "$name.png"))
        $width=[Convert]::ToInt32([Convert]::ToHexString($png[16..19]),16)
        $height=[Convert]::ToInt32([Convert]::ToHexString($png[20..23]),16)
        $capturePixels="${width}x${height}"
        $checks+=Check 'WindowManager reports the selected logical viewport' $displayProfile $logicalProfile
        [int]$logicalWidth,[int]$logicalHeight=$logicalProfile.Split('x')
        $outside=@(foreach($node in $nodes){$box=Bounds $node;if($box[2]-gt$logicalWidth-or$box[3]-gt$logicalHeight){$node}})
        $checks+=Check 'Visible hierarchy stays inside the logical viewport' 0 $outside.Count
    }
    if(-not$checks.Count){throw 'A visual checkpoint needs evaluated comparisons.'}
    $points.Add([pscustomobject]@{title=$Title;image="$name.png";hierarchy="$name-hierarchy.txt";captureMillis=$watch.ElapsedMilliseconds;logicalViewport=$logicalProfile;capturePixels=$capturePixels;actions=$actions.ToArray();checks=$checks})
    $actions.Clear()
    $script:lastObservation=$nodes
    foreach($check in $checks){if(-not$check.passed){throw "Native assertion failed: $($check.name)"}}
}
function Text($nodes){return (@($nodes|ForEach-Object{$_.text+' '+$_.description+' '+$_.'content-desc'}))-join' | '}
function BackFromHelp {
    $watch=[Diagnostics.Stopwatch]::StartNew()
    Adb @('shell','input','keyevent','KEYCODE_BACK') 'help-back'|Out-Null
    $watch.Stop()
    $actions.Add([pscustomobject]@{operation='Android back key';control='Help navigation';elapsedMillis=$watch.ElapsedMilliseconds})
}
function HelpAwardChecks($Nodes,[string]$Heading,[string]$Players,[string]$Place,[string]$Points){
    Check 'Localized finishing awards heading' 1 @($Nodes|Where-Object text -ceq $Heading).Count
    $cells=@($Nodes|Where-Object id -like '*:id/rentz_finishing_award')
    Check 'Eighteen awards for the four supported tables' 18 $cells.Count
    $expected=@(300,200,100,0,0,0)
    foreach($playersCount in 3..6){
        Check "Table row for $playersCount players" 1 @($Nodes|Where-Object description -ceq "$playersCount $Players").Count
        for($position=1;$position-le$playersCount;$position++){
            $pointsValue=$expected[$position-1]
            $description="$playersCount $Players, $Place $position, $pointsValue $Points"
            $matches=@($cells|Where-Object description -ceq $description)
            $actual=if($matches.Count-eq1){$matches[0].text}else{"$($matches.Count) visible cells"}
            Check "Table $playersCount, place $position" $pointsValue $actual
        }
    }
}
function RunHelpAwardsSanity {
    Tap 'How to play'
    Observe 'English help exposes the 3–6 player finishing awards' {param($nodes)
        HelpAwardChecks $nodes 'Rentz finishing awards' 'players' 'place' 'points'
    }
    BackFromHelp
    Tap 'Interface language';Tap 'Română';Tap 'Cum se joacă'
    Observe 'Romanian help keeps the same finishing awards' {param($nodes)
        HelpAwardChecks $nodes 'Premii de sosire Rentz' 'jucători' 'locul' 'puncte'
    }
    BackFromHelp
    $session=Adb @('shell','run-as','ro.rentzlab.app.uisanity','ls','files/session.json') 'help-session-check' -Allowed @(0,1)
    Observe 'Returning from help keeps the new-game menu and creates no session' {param($nodes)
        Check 'Romanian home remains available' 1 @($nodes|Where-Object text -ceq 'Partidă nouă').Count
        Check 'Resume remains disabled' 1 @($nodes|Where-Object{$_.text-ceq'Continuă partida'-and$_.enabled-eq'false'}).Count
        Check 'No saved match file' $true ($session.ExitCode-eq1-and($session.Output+$session.Error)-match'No such file or directory')
    }
}
function HistorySwipe([int[]]$Box,[switch]$Across,[int]$Duration=320,[int]$Distance=0,[switch]$Reverse,[ValidateRange(1,4)][int]$Repeat=1){
    $width=$Box[2]-$Box[0];$height=$Box[3]-$Box[1]
    $x=[int]($Box[0]+$width*.88);$y=[int]($Box[1]+$height*.85)
    $endX=if($Across){[int]($Box[0]+$width*.12)}else{$x}
    $endY=if($Across){$y}else{[int]($Box[1]+$height*.15)}
    if($Distance){$endY=[Math]::Max($endY,$y-$Distance)}
    if($Reverse){$y,$endY=$endY,$y}
    $axis=if($Across){'Score columns'}else{'Completed hands'}
    $gesture="input swipe $x $y $endX $endY $Duration"
    $command=@(for($i=0;$i-lt$Repeat;$i++){$gesture}) -join ' && '
    $watch=[Diagnostics.Stopwatch]::StartNew()
    Adb @('shell',$command) 'history-swipe'|Out-Null
    $watch.Stop()
    $actions.Add([pscustomobject]@{operation='Swipe Android history';control="$axis ($Repeat gestures)";bounds="$x,$y to $endX,$endY";elapsedMillis=$watch.ElapsedMilliseconds})
}
function HistoryHand($nodes,$point){
    $chooser=SeatName $point.chooser
    @($nodes|Where-Object{($_.text -replace '\s+',' ')-ceq"$($point.hand) · $($point.label) $chooser"})
}
function HistoryPoint($nodes,$point,[int]$Seat,[string]$PinnedBounds,[int]$FullScoreWidth=0,[int]$FullRowHeight=0,[switch]$WholeWindow){
    $name=SeatName $Seat
    $hand=@(HistoryHand $nodes $point)
    $corner=@($nodes|Where-Object{$_.text-ceq'Hand · game'})
    $player=@($nodes|Where-Object{$_.text-ceq$name})
    $label="$name. Δ $($point.delta[$Seat]). Σ $($point.total[$Seat]). Hand $($point.hand)"
    $score=@($nodes|Where-Object{$_.description-ceq$label})
    Check "Confirmed hand $($point.hand) remains readable" 1 $hand.Count
    Check "Exact signed score for $name on hand $($point.hand)" 1 $score.Count
    if(-not$WholeWindow){
        Check 'Pinned hand heading remains visible' 1 $corner.Count
        Check "Player heading $name remains visible" 1 $player.Count
        Check 'Legend remains visible below the rows' 1 @($nodes|Where-Object{$_.text-ceq'Δ: hand points · Σ: confirmed total'}).Count
    }
    if($corner.Count-eq1-and$PinnedBounds){Check 'Pinned heading keeps its screen rectangle' $PinnedBounds $corner[0].bounds}
    if($score.Count-eq1-and$hand.Count-eq1){
        $s=Bounds $score[0];$h=Bounds $hand[0]
        Check 'Score and hand row share vertical bounds' "$($h[1]),$($h[3])" "$($s[1]),$($s[3])"
        if($player.Count-eq1){
            $p=Bounds $player[0]
            Check 'Player heading and score group share horizontal bounds' "$($p[0]),$($p[2])" "$($s[0]),$($s[2])"
        }
        Check 'Scores never overlap pinned hand metadata' $true ($s[0]-ge$h[2])
        if($FullScoreWidth){Check 'Full player score group is visible' $FullScoreWidth ($s[2]-$s[0])}
        if($FullRowHeight){Check 'Full confirmed row height is visible' $FullRowHeight ($h[3]-$h[1])}
    }
}
function DisplayOverride([string]$Receipt,[string]$Property,[string]$Pattern){
    $match=[regex]::Match($Receipt,"(?m)^Override $($Property): ($Pattern)\s*$")
    if($match.Success){return $match.Groups[1].Value}
    if($Receipt-match"Override $($Property):"){throw "Unexpected Android $Property override"}
    return 'reset'
}
function SetHistorySize([string]$Size){
    $watch=[Diagnostics.Stopwatch]::StartNew()
    Adb @('shell','wm','size',$Size) 'history-viewport'|Out-Null
    $windowManager=(Adb @('shell','dumpsys','window','displays') 'history-logical-viewport').Output
    $sizeMatch=[regex]::Match($windowManager,'(?m)^\s*init=\d+x\d+[^\r\n]*?\bcur=(\d+x\d+)\b')
    if(-not$sizeMatch.Success){throw 'WindowManager did not expose the logical display size.'}
    $script:logicalProfile=$sizeMatch.Groups[1].Value
    $watch.Stop()
    $script:displayProfile=$Size
    $actions.Add([pscustomobject]@{operation='Set Android history viewport';control='Display size';value=$Size;elapsedMillis=$watch.ElapsedMilliseconds})
}
function HistoryElement($Tree,[string]$Id){
    $found=@($Tree|Where-Object{$_.'resource-id'-like"*:id/$Id"})
    if($found.Count-ne1){throw "Expected one visible Android $Id, found $($found.Count)."}
    return $found[0]
}
function ReadHistoryLegend($Nodes){return @($Nodes|Where-Object{$_.text-ceq'Δ: hand points · Σ: confirmed total'})}
function OpenHandDetails($Point){
    $hand=@(HistoryHand @(Ui) $Point|Where-Object clickable -eq 'true')
    if($hand.Count-ne1){throw "One clickable confirmed hand expected: $($Point.hand)"}
    TapNode $hand[0] "Hand $($Point.hand) scoring details"
}
function SeatName([int]$Seat){if($Seat-eq0){'You'}else{$script:historyOpponents[$Seat-1]}}
function DetailSeat($Nodes,$Point,[int]$Place){
    $seat=$Point.finishOrder[$Place-1]
    $label="$Place. $(SeatName $seat)"
    $head=@($Nodes|Where-Object text -ceq $label)
    Check "Finishing place $Place" 1 $head.Count
    if($head.Count-eq1){
        $index=[Array]::IndexOf([object[]]$Nodes,$head[0])
        $score=$Nodes[$index+1]
        $award=$Nodes[$index+2]
        Check "Confirmed delta and total for $label" "Δ $($Point.delta[$seat]) · Σ $($Point.total[$seat])" ($score.text -replace '\s+',' ')
        Check "Finishing award for $label" "Finishing award · $($Point.delta[$seat])" ($award.text -replace '\s+',' ')
    }
}
function RunLastHandDetails([int]$FullWidth) {
    OpenHandDetails $fixture.last
    Observe 'Restored Rentz details show the recorded first finishing place' {param($nodes)
        Check 'Confirmed Rentz hand title' 1 @($nodes|Where-Object text -ceq 'Hand 48 · Rentz').Count
        Check 'Recorded chooser and profile' 1 @($nodes|Where-Object {($_.text -replace '\s+',' ')-ceq"Chosen by $(SeatName $fixture.last.chooser) Scoring profile · LABORATORY_V1"}).Count
        DetailSeat $nodes $fixture.last 1
    }
    $tree=@(Ui)
    $scroll=Bounds (HistoryElement $tree 'history_details')
    for($attempt=0;$attempt-lt6-and-not($tree.text-contains'Last place is assigned to the player still holding cards.');$attempt++){
        HistorySwipe $scroll
        $tree=@(Ui)
    }
    Observe 'Equal awards retain the actual final places after restoration' {param($nodes)
        foreach($place in 4..6){DetailSeat $nodes $fixture.last $place}
        Check 'Assigned last place is explained' 1 @($nodes|Where-Object text -ceq 'Last place is assigned to the player still holding cards.').Count
    }
    Tap 'Back to history'
    Observe 'Returning from details preserves the final row and score column' {param($nodes)
        HistoryPoint $nodes $fixture.last 5 '' $fullWidth
    }
}
function ActiveHistoryScrollerCount($Tree,[string]$Id){
    @($Tree|Where-Object{$_.'resource-id'-like"*:id/$Id"-and$_.scrollable-eq'true'}).Count
}
function RunShortHistory([int]$FullScoreWidth,[int]$FullRowHeight,[int]$FullLegendHeight){
    Tap 'Close';$null=State -Screen 'MENU'
    Tap 'Scores';$null=State -Screen 'HISTORY'
    SetHistorySize '720x740'
    $tree=@(Ui)
    $outer=HistoryElement $tree 'history_content'
    $heading=HistoryElement $tree 'history_headings'
    Observe 'Short window uses the outer vertical scroller' {param($nodes)
        Check 'Whole history scrolls vertically' 'true' $outer.scrollable
        Check 'The inner viewport yields vertical scrolling to the outer history' 0 (ActiveHistoryScrollerCount $tree 'history_body')
        Check 'Short window keeps the history heading' 1 @($nodes|Where-Object{$_.text-ceq'Hand · game'}).Count
    }
    $columnBox=Bounds $heading
    for($attempt=0;$attempt-lt8;$attempt++){
        $lastHeading=@($tree|Where-Object{$_.text-ceq(SeatName 5)})
        if($lastHeading.Count-eq1){$b=Bounds $lastHeading[0];if($b[2]-$b[0]-eq$FullScoreWidth){break}}
        HistorySwipe $columnBox -Across -Duration 200
        $tree=@(Ui)
    }
    Observe 'Short window reaches the full last player heading' {param($nodes)
        $lastHeading=@($nodes|Where-Object{$_.text-ceq(SeatName 5)})
        Check 'Last player is visible in the short header' 1 $lastHeading.Count
        if($lastHeading.Count-eq1){$b=Bounds $lastHeading[0];Check 'Full last player heading width' $FullScoreWidth ($b[2]-$b[0])}
    }
    $bodyBox=Bounds $outer;$bodyBox[2]=$columnBox[0]
    for($attempt=0;$attempt-lt96;){
        $last=@(HistoryHand $tree $fixture.last)
        if($last.Count-eq1){
            $b=Bounds $last[0]
            $missing=$FullRowHeight-($b[3]-$b[1])
            if($missing-eq0){break}
            HistorySwipe $bodyBox -Duration 480 -Distance ([Math]::Max(18,$missing+18)) -Reverse:($b[1]-le$bodyBox[1]+1)
            $attempt++
        }else{
            $batch=[Math]::Min(4,96-$attempt)
            HistorySwipe $bodyBox -Duration 110 -Repeat $batch
            $attempt+=$batch
        }
        $tree=@(Ui)
    }
    Observe 'Short window exposes the complete last hand and signed last-player score' {param($nodes)
        HistoryPoint $nodes $fixture.last 5 '' $FullScoreWidth $FullRowHeight -WholeWindow
    }
    $legends=@(ReadHistoryLegend $lastObservation)
    $legendHeight=0
    if($legends.Count-eq1){$b=Bounds $legends[0];$legendHeight=$b[3]-$b[1]}
    if($legendHeight-lt$FullLegendHeight){HistorySwipe $bodyBox}
    Observe 'Short window exposes the complete legend at the end of content' {param($nodes)
        $legends=@(ReadHistoryLegend $nodes)
        Check 'Legend remains reachable by scrolling' 1 $legends.Count
        if($legends.Count-eq1){$b=Bounds $legends[0];Check 'Legend has its full enlarged height' $FullLegendHeight ($b[3]-$b[1])}
    }
    SetHistorySize '720x1280'
    $tree=@(Ui)
    $rows=HistoryElement $tree 'history_body'
    Observe 'Growing the open window restores pinned history' {param($nodes)
        Check 'The inner row viewport scrolls again' 'true' $rows.scrollable
        Check 'The outer history yields vertical scrolling to the rows' 0 (ActiveHistoryScrollerCount $tree 'history_content')
        Check 'Heading returns above the rows' 1 @($nodes|Where-Object{$_.text-ceq'Hand · game'}).Count
        Check 'Legend returns below the rows' 1 @(ReadHistoryLegend $nodes).Count
    }
}
function RunHistorySanity {
    Tap 'Scores'
    Observe 'An empty history has an explicit native state' {param($nodes) Check 'Empty history text' $true ((Text $nodes).Contains('No completed hands yet'))}
    Tap 'Close'
    $historyJournal=Join-Path $raw 'development-journal.json'
    Copy-Item -LiteralPath $journal -Destination $historyJournal
    $token=Start-DevelopmentExposure
    Complete-DevelopmentExposure $historyJournal $token
    Adb @('shell','am','force-stop','ro.rentzlab.app.uisanity') 'stop-before-history-fixture'|Out-Null
    $script:journalPushed=$true
    Adb @('push',$journal,$remoteJournal) 'stage-qualified-history'|Out-Null
    Adb @('shell','run-as','ro.rentzlab.app.uisanity','mkdir','-p','files') 'history-data-directory'|Out-Null
    Adb @('shell','run-as','ro.rentzlab.app.uisanity','cp',$remoteJournal,'files/session.json') 'install-history-fixture'|Out-Null
    $read=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/session.json') 'read-installed-history'
    if((Get-FileHash -LiteralPath $read.File).Hash-ne$fixture.journalSha256){throw 'Installed journal bytes differ from the qualified fixture.'}

    if($HistoryMatrix){
        $script:restoreSize=DisplayOverride $initialSize 'size' '\d+x\d+'
        $script:restoreDensity=DisplayOverride $initialDensity 'density' '\d+'
        $script:restoreFont=$initialFont.Trim()
        if($restoreFont-notmatch'^(?:null|\d+(?:\.\d+)?)$'){throw 'Unexpected original Android font scale'}
        $script:displayChanged=$true
        Adb @('shell','wm','density','360') 'history-density'|Out-Null
        Adb @('shell','settings','put','system','font_scale','1.5') 'history-enlarged-font'|Out-Null
        SetHistorySize '720x1280'
        $font=(Adb @('shell','settings','get','system','font_scale') 'effective-history-font').Output.Trim()
        $density=(Adb @('shell','wm','density') 'effective-history-density').Output
    }
    Adb @('shell','am','start','-W','-n','ro.rentzlab.app.uisanity/ro.rentzlab.app.MainActivity') 'launch-history-fixture'|Out-Null
    Tap 'Scores';$opened=State -Screen 'HISTORY'
    $script:historyOpponents=@($opened.opponents)
    if($script:historyOpponents.Count-ne5){throw 'Restored history needs the five opponent names in seat order.'}
    Observe 'A qualified 48-hand journal opens the first confirmed row' {param($nodes)
        if($HistoryMatrix){Check 'Effective enlarged system font' '1.5' $font;Check 'Effective history density' '360' (DisplayOverride $density 'density' '\d+')}
        Check 'Restored legal command revision' $fixture.revision $opened.revision
        Check 'Six engine seats' 6 $opened.view.players
        Check 'Complete match after journal restore' 'MATCH_END' $opened.view.phase
        Check 'Confirmed totals after journal restore' ($fixture.last.total-join',') ($opened.view.totals-join',')
        HistoryPoint $nodes $fixture.first 0 ''
    }
    if($HistoryDetails){
        OpenHandDetails $fixture.first
        Observe 'Blind trick detail shows counted contributions and the multiplier' {param($nodes)
            Check 'Blind hand identified' 1 @($nodes|Where-Object text -ceq 'Hand 1 · Tricks ×2').Count
            $seat=0..($fixture.players-1)|Where-Object{$fixture.first.delta[$_]-ne0}|Select-Object -First 1
            $name=SeatName $seat
            $head=@($nodes|Where-Object text -ceq $name)
            Check 'Scoring seat in confirmed detail' 1 $head.Count
            if($head.Count-eq1){
                $index=[Array]::IndexOf([object[]]$nodes,$head[0])
                $contribution=$nodes[$index+2]
                Check "Captured tricks and doubled award for $name" "Tricks × $(-$fixture.first.delta[$seat]/20) · $($fixture.first.delta[$seat])" ($contribution.text -replace '\s+',' ')
            }
        }
        Tap 'Back to history'
    }
    $tree=@(Ui)
    $corner=@($tree|Where-Object{$_.text-ceq'Hand · game'})[0].bounds
    $ownHeading=Bounds (@($tree|Where-Object{$_.text-ceq'You'})[0])
    $fullWidth=$ownHeading[2]-$ownHeading[0]
    $scoresBox=Bounds (@($tree|Where-Object{$_.'resource-id'-like'*:id/history_scores'})[0])
    for($attempt=0;$attempt-lt8-and-not($tree.text-contains(SeatName 5));$attempt++){
        HistorySwipe $scoresBox -Across
        $tree=@(Ui)
    }
    HistorySwipe $scoresBox -Across
    Observe 'Horizontal swipe reaches the last player under its own heading' {param($nodes) HistoryPoint $nodes $fixture.first 5 $corner $fullWidth}
    $bodyBox=Bounds (@($tree|Where-Object{$_.'resource-id'-like'*:id/history_body'})[0])
    # Use the pinned side for vertical gestures to exercise the shared row viewport.
    $bodyBox[2]=$scoresBox[0]
    $lastCaption="$($fixture.last.hand) · $($fixture.last.label)"
    for($attempt=0;$attempt-lt12-and-not(@($tree|Where-Object{([string]$_.text).StartsWith($lastCaption)}).Count);$attempt++){
        if($HistoryMatrix){HistorySwipe $bodyBox -Duration 160 -Repeat 4}else{HistorySwipe $bodyBox}
        $tree=@(Ui)
    }
    HistorySwipe $bodyBox
    Observe 'Vertical swipe reaches the final hand and retains both axes of context' {param($nodes) HistoryPoint $nodes $fixture.last 5 $corner $fullWidth}
    if($HistoryDetails){RunLastHandDetails $fullWidth}
    if($HistoryMatrix){
        $last=Bounds (@(HistoryHand $lastObservation $fixture.last)[0])
        $legend=Bounds (@(ReadHistoryLegend $lastObservation)[0])
        RunShortHistory $fullWidth ($last[3]-$last[1]) ($legend[3]-$legend[1])
    }
    Tap 'Close';$closed=State -Screen 'MENU'
    Observe 'Closing history returns to the menu with the same confirmed match' {param($nodes)
        Check 'Menu remains available' $true ((Text $nodes).Contains('Scores'))
        Check 'History gestures preserve the command revision' $fixture.revision $closed.revision
        Check 'All final totals remain confirmed' ($fixture.last.total-join',') ($closed.view.totals-join',')
    }
}
function TableChecks($nodes,$state){
    $seats=@($nodes|Where-Object{$_.description-match'\. Points: .*\. Total:'})
    Check 'All player frames exposed' $state.view.players $seats.Count
    foreach($name in $state.opponents){
        Check "Named opponent $name occupies one frame" 1 @($seats|Where-Object{$_.description.StartsWith($name+'. ')}).Count
    }
    $hand=@($nodes|Where-Object{$_.description-like'Your hand:*'})
    Check 'One native hand region' 1 $hand.Count
    $docText=Text $nodes
    Check 'No vertical game scroll' 0 @($nodes|Where-Object{$_.class-eq'android.widget.ScrollView'-and$_.scrollable-eq'true'}).Count
    foreach($card in $state.view.hand){
        $rank=switch([int]$card.rank){11{'J'}12{'Q'}13{'K'}14{'A'}default{[string]$card.rank}}
        $suit=@{CLUBS='♣';DIAMONDS='♦';HEARTS='♥';SPADES='♠'}[[string]$card.suit];$label=$rank+$suit
        $matches=@($nodes|Where-Object{$_.description-eq('Play '+$label)-or$_.description-eq($label+'. Unavailable')})
        Check "Visible own card $label" 1 $matches.Count
        if($matches.Count-eq1-and$hand.Count-eq1){
            $cardBox=Bounds $matches[0];$handBox=Bounds $hand[0]
            Check "Card remains in hand bounds $label" $true ($cardBox[0]-ge$handBox[0]-and$cardBox[2]-le$handBox[2]-and$cardBox[1]-ge$handBox[1]-and$cardBox[3]-le$handBox[3])
        }
    }
    $expectedOrder=@($state.view.hand | Sort-Object @{Expression={@('SPADES','HEARTS','CLUBS','DIAMONDS').IndexOf([string]$_.suit)}}, @{Expression={[int]$_.rank}} | ForEach-Object{
        $rankValue=[int]$_.rank
        $rank=switch($rankValue){11{'J'}12{'Q'}13{'K'}14{'A'}default{[string]$rankValue}}
        $rank+@{CLUBS='♣';DIAMONDS='♦';HEARTS='♥';SPADES='♠'}[[string]$_.suit]
    })
    $visibleOrder=@($nodes|Where-Object{$_.description-match'^(?:Play )?((?:\d+|[JQKA])[♣♦♥♠])(?:\. Unavailable)?$'}|
        Sort-Object @{Expression={(Bounds $_)[0]}}|ForEach-Object{$_.description-replace'^Play |\. Unavailable$',''})
    Check 'Actual left-to-right hand order' ($expectedOrder-join' ') ($visibleOrder-join' ')
    if($hand.Count-eq1){$handBox=Bounds $hand[0];foreach($seat in $seats){$box=Bounds $seat;Check ('Seat clears hand: '+$seat.description) $true ($box[3]-le$handBox[1])}}
}
function SeatGeometry($nodes,$state){
    $names=@('Ana')+@($state.opponents);$rectangles=[Collections.Generic.List[string]]::new()
    for($seat=0;$seat-lt$state.view.players;$seat++){
        $name=[string]$names[$seat]
        $frames=@($nodes|Where-Object{$_.description.StartsWith($name+'. Points:')})
        if($frames.Count-ne1){throw "One visible frame required for seat $seat ($name)."}
        $rectangles.Add("$seat=$($frames[0].bounds)")
    }
    return ($rectangles -join ';')
}
function RentzSequenceChecks($nodes,$state){
    $cellPattern='^(?:Empty slot )?(?:[2-9]|10|J|Q|K|A)[♣♦♥♠]$'
    $cells=@($nodes|Where-Object{[regex]::IsMatch([string]$_.description,$cellPattern)})
    $expected=8*$state.view.players
    Check 'Four complete public suit sequences are rendered' $expected $cells.Count
    $board=@($nodes|Where-Object{$_.description-eq'Rentz table'})
    Check 'One accessible board contains the sequence grid' 1 $board.Count
    if($board.Count-eq1){
        $boardBox=Bounds $board[0]
        $inside=@(foreach($cell in $cells){$box=Bounds $cell;if($box[0]-lt$boardBox[0]-or$box[2]-gt$boardBox[2]-or$box[1]-lt$boardBox[1]-or$box[3]-gt$boardBox[3]){$cell}})
        Check 'Every sequence slot remains inside the table viewport' 0 $inside.Count
    }
    $rows=@($cells|ForEach-Object{(Bounds $_)[1]}|Sort-Object -Unique)
    Check 'Rentz cards remain grouped into exactly four suit rows' 4 $rows.Count
    foreach($row in $rows){Check "One rank slot per card in suit row $row" (2*$state.view.players) @($cells|Where-Object{(Bounds $_)[1]-eq$row}).Count}
    $played=@($cells|Where-Object{$_.description-notlike'Empty slot *'})
    Check 'Publicly played cards match the actor board exactly' $state.view.board.Count $played.Count
    $widths=@($cells|ForEach-Object{$box=Bounds $_;$box[2]-$box[0]})
    $heights=@($cells|ForEach-Object{$box=Bounds $_;$box[3]-$box[1]})
    $measurement=if($widths.Count){"$([int](($widths|Measure-Object -Minimum).Minimum))–$([int](($widths|Measure-Object -Maximum).Maximum)) px wide; $([int](($heights|Measure-Object -Minimum).Minimum))–$([int](($heights|Measure-Object -Maximum).Maximum)) px high"}else{'no native slot bounds'}
    [pscustomobject]@{name='Measured Rentz slot bounds';expected='Native bounds captured for every sequence slot';actual=$measurement;passed=($cells.Count-eq$expected)}
}
function CardLabel($card){
    $rank=switch([int]$card.rank){11{'J'}12{'Q'}13{'K'}14{'A'}default{[string]$card.rank}}
    return $rank+@{CLUBS='♣';DIAMONDS='♦';HEARTS='♥';SPADES='♠'}[[string]$card.suit]
}
function HumanContractLabel([string]$contract){
    return @{TRICKS='Tricks';QUEENS='Queens';DIAMONDS='Diamonds';KING_HEARTS='King of Hearts';TOTALS='Totals';WHIST='Whist';TEN_CLUBS='Ten of Clubs';RENTZ='Rentz'}[$contract]
}
function ChooseHumanSubgame($state,[switch]$ForceRentz){
    if(-not$state.view.revealed){$revision=$state.revision;Tap 'Show cards';$state=State -After $revision}
    $available=@($state.view.available)
    $contract=if($ForceRentz){'RENTZ'}elseif($available -contains 'TRICKS'){'TRICKS'}else{@($available|Where-Object{$_-ne'RENTZ'}|Select-Object -First 1)[0]}
    if(-not$contract-or$available -notcontains$contract){throw "Required human subgame is unavailable: $contract"}
    Tap 'Choose game';Tap (HumanContractLabel $contract)
    return (State -After $state.revision)
}
function RunConsecutiveSubgames($state,[string]$journalFile){
    $selectedContracts=[Collections.Generic.List[string]]::new();$baseSeats='';$playedNormal=$false
    for($deal=1;$deal-le3;$deal++){
        if($deal-eq1){
            if($state.view.phase-ne'CHOOSE'-or$state.view.turn-ne0){throw 'The first subgame must begin at the human chooser.'}
            $state=ChooseHumanSubgame $state -ForceRentz
        }else{
            if($state.view.phase-ne'DEAL_END'){throw "Subgame $($deal-1) did not reach a completed deal."}
            $revision=$state.revision;Tap 'Next hand';$state=State -After $revision
            $choiceSteps=0
            while($state.view.phase-eq'CHOOSE'){
                if($state.view.turn-eq0){$state=ChooseHumanSubgame $state}
                else{$state=State -After $state.revision}
                if(++$choiceSteps-gt8){throw 'The next chooser did not resolve through the UI flow.'}
            }
        }
        if($state.view.phase-ne'PLAY'){throw "Subgame $deal did not enter PLAY."}
        $contract=[string]$state.view.contract;$selectedContracts.Add($contract)
        if($deal-eq1-and$contract-ne'RENTZ'){throw 'The first visible subgame must be Rentz.'}
        if($deal-gt1-and$contract-eq'RENTZ'){throw "Subgame $deal must be a non-Rentz contrast after the opening Rentz subgame."}
        if($contract-ne'RENTZ'){$playedNormal=$true}
        $nodes=@(Ui);$seatGeometry=SeatGeometry $nodes $state
        if($deal-eq1){$baseSeats=$seatGeometry}
        $title="Consecutive subgame $deal starts: $contract"
        Observe $title {param($captured)
            TableChecks $captured $state
            Check 'Four-player table uses the same seat rectangles in this viewport' $baseSeats $seatGeometry
            Check 'Visible subgame matches the public actor state' $contract $state.view.contract
            if($contract-eq'RENTZ'){RentzSequenceChecks $captured $state}
            else{Check 'A non-sequence contract is available for geometry comparison' $true $true}
        }
        $steps=0;$firstOwnMoveCaptured=$false
        while($state.view.phase-eq'PLAY'){
            $before=$state
            if($state.view.turn-eq0-and@($state.view.legal).Count-gt0){
                $card=$state.view.legal[0];Tap ('Play '+(CardLabel $card));$state=State -After $before.revision
                if(-not$firstOwnMoveCaptured){
                    $firstOwnMoveCaptured=$true
                    Observe "Subgame $deal first human move remains visible" {param($captured)
                        TableChecks $captured $state
                        Check 'The actor revision advanced after the UI play' $true ($state.revision-gt$before.revision)
                        Check 'Exactly the selected human card left the hand' ($before.view.hand.Count-1) $state.view.hand.Count
                        Check 'The same seat geometry survives a card play' $baseSeats (SeatGeometry $captured $state)
                    }
                }
            }else{$state=State -After $before.revision}
            if(++$steps-gt128){throw "Subgame $deal exceeded its bounded public action count."}
        }
        if($state.view.phase-ne'DEAL_END'){throw "Subgame $deal ended in an unexpected phase: $($state.view.phase)."}
        $terminal=$state
        Observe "Subgame $deal completed: $contract" {param($captured)
            Check 'Exact completed hand number' $deal $terminal.view.handNumber
            Check 'Completed subgame contract' $contract $terminal.view.contract
            Check 'One score value per player' $terminal.view.players $terminal.view.dealPoints.Count
            if($contract-eq'RENTZ'){
                Check 'Rentz finishing order contains each player' $terminal.view.players $terminal.view.finishOrder.Count
                RentzSequenceChecks $captured $terminal
            }
        }
        $startTitle="Consecutive subgame $deal starts: $contract"
        $moveTitle="Subgame $deal first human move remains visible"
        $completeTitle="Subgame $deal completed: $contract"
        if(@($points|Where-Object{$_.title-ceq$startTitle}).Count-ne1-or
            @($points|Where-Object{$_.title-ceq$moveTitle}).Count-ne1-or
            @($points|Where-Object{$_.title-ceq$completeTitle}).Count-ne1){
            throw "Game $deal did not produce all required native display checkpoints."
        }
        $completedGames.Add([ordered]@{number=$deal;contract=$contract;phase=$terminal.view.phase;handNumber=$terminal.view.handNumber;scoreCount=$terminal.view.dealPoints.Count;
            seatGeometryVerified=$true;firstHumanMoveVerified=$firstHumanMoveCaptured;completionVerified=($terminal.view.phase-eq'DEAL_END');
            rentzFinishOrderVerified=($contract-eq'RENTZ'-and$terminal.view.finishOrder.Count-eq$terminal.view.players)})
    }
    if(-not$playedNormal){throw 'The three-subgame sample did not include a non-Rentz contract for visual comparison.'}
    $journal=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/session.json') 'three-subgame-journal'
    $journalPath=Join-Path $raw 'three-subgame-journal.json';[IO.File]::WriteAllText($journalPath,$journal.Output,[Text.UTF8Encoding]::new($false))
    $actor=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/actor-view.json') 'three-subgame-actor'
    $actorPath=Join-Path $raw 'three-subgame-actor.json';[IO.File]::WriteAllText($actorPath,$actor.Output,[Text.UTF8Encoding]::new($false))
    $oracle=Invoke-DevelopmentAudit $journalPath $actorPath
    if($oracle.status-ne'PASS'-or$oracle.completedDeals-ne3-or$oracle.phase-ne'DEAL_END'){throw 'Three-deal independent journal replay did not match the visible application state.'}
    $oracleContracts=@($oracle.history|ForEach-Object{$_.contract})
    Tap 'Score history';$history=State -Screen 'HISTORY'
    Observe 'Score history contains the three consecutive subgames' {param($captured)
        $rows=@($captured|Where-Object{$_.text-match'(?m)^\d+ · '})
        Check 'Three completed history rows are visible' 3 $rows.Count
        if($rows.Count-eq3){for($index=0;$index-lt3;$index++){
            $label=HumanContractLabel $selectedContracts[$index]
            Check "History row $($index+1) retains its actual contract" $true (($rows[$index].text)-match("(?m)^"+($index+1)+" · "+[regex]::Escape($label)))
        }}
        Check 'Independent journal replay preserves the three visible contracts' ($selectedContracts -join ',') ($oracleContracts -join ',')
        Check 'Rentz occurs in the independently replayed match segment' $true ($oracleContracts -contains 'RENTZ')
        Check 'At least one standard contract occurs in the same segment' $true (@($oracleContracts|Where-Object{$_-ne'RENTZ'}).Count-gt0)
        Check 'Independent audit saw all human-visible commands' $true ($oracle.auditedTransitions-gt0)
        Check 'Score history state belongs to the final application revision' $terminal.revision $history.revision
        Check 'Rentz history row is present' $true ((Text $captured).Contains('Rentz'))
    }
    Tap 'Close';$null=State;Tap 'Main menu'
    Observe 'The match with three subgames remains resumable' {param($captured)
        Check 'Resume remains enabled' 1 @($captured|Where-Object{$_.text-eq'Resume game'-and$_.enabled-eq'true'}).Count
    }
    Adb @('shell','am','force-stop','ro.rentzlab.app.uisanity') 'three-subgame-restart-stop'|Out-Null
    Adb @('shell','am','start','-W','-n','ro.rentzlab.app.uisanity/ro.rentzlab.app.MainActivity') 'three-subgame-restart-start'|Out-Null
    Tap 'Resume game';Tap 'View table';$restored=State
    Observe 'Three-deal match restores its final contract and scores' {param($captured)
        Check 'Restored revision equals the terminal revision' $terminal.revision $restored.revision
        Check 'Restored completed phase' 'DEAL_END' $restored.view.phase
        Check 'Restored final contract' $terminal.view.contract $restored.view.contract
        Check 'Restored totals' ($terminal.view.totals -join ',') ($restored.view.totals -join ',')
        Check 'Restored seat geometry' $baseSeats (SeatGeometry $captured $restored)
        TableChecks $captured $restored
    }
    Tap 'Options';Tap 'Score history';$null=State -Screen 'HISTORY'
    Observe 'The three confirmed rows remain after process restart' {param($captured)
        $rows=@($captured|Where-Object{$_.text-match'(?m)^\d+ · '})
        Check 'Restarted history retains all three completed subgames' 3 $rows.Count
        Check 'Restarted history still includes Rentz' $true ((Text $captured).Contains('Rentz'))
        Check 'Restarted history retains the final contract' $true ((Text $captured).Contains((HumanContractLabel $selectedContracts[2])))
    }
    $script:threeSubgameContracts=$selectedContracts.ToArray();$script:threeSubgameOracle=$oracle
}
function RunCompactScores($terminal){
    Tap 'Close';$null=State
    Tap 'Options';Tap 'Main menu';Tap 'Interface language';Tap 'Română'
    $script:restoreSize=DisplayOverride $initialSize 'size' '\d+x\d+'
    $script:restoreDensity=DisplayOverride $initialDensity 'density' '\d+'
    $script:restoreFont=$initialFont.Trim()
    if($restoreFont-notmatch'^(?:null|\d+(?:\.\d+)?)$'){throw 'Unexpected original Android font scale'}
    $script:displayChanged=$true
    Adb @('shell','wm','density','360') 'score-density'|Out-Null
    Adb @('shell','settings','put','system','font_scale','1.5') 'score-enlarged-font'|Out-Null
    SetHistorySize '720x1280'
    $font=(Adb @('shell','settings','get','system','font_scale') 'score-effective-font').Output.Trim()
    Tap 'Continuă partida';Tap 'Vezi masa';$state=State
    Observe 'Romanian signed scores stay on two lines per seat at enlarged font' {param($nodes)
        Check 'Effective enlarged system font' '1.5' $font
        Check 'Logical Android viewport' '720x1280' $logicalProfile
        Check 'Same confirmed revision after resize and resume' $terminal.revision $state.revision
        Check 'Same opponents after switching language' ($terminal.opponents-join',') ($state.opponents-join',')
        Check 'There is a negative score to render' $true (@($state.view.dealPoints|Where-Object{$_-lt0}).Count-gt0)
        $names=@('Ana')+@($state.opponents)
        for($seat=0;$seat-lt$names.Count;$seat++){
            $points=$state.view.dealPoints[$seat];$total=$state.view.totals[$seat]
            $pointsLine="Δ $points"
            $totalLine="Σ $total"
            $description="$($names[$seat]). Puncte: $points. Total: $total"
            $frame=@($nodes|Where-Object description -ceq $description)
            Check "Seat $seat has both complete facts in reading order" 1 $frame.Count
            if($frame.Count-ne1){continue}
            $bounds=Bounds $frame[0]
            $lines=@($nodes|Where-Object {
                if($_.text-cnotin@($pointsLine,$totalLine)){return $false}
                $line=Bounds $_
                $line[0]-ge$bounds[0]-and$line[2]-le$bounds[2]-and$line[1]-ge$bounds[1]-and$line[3]-le$bounds[3]
            }|Sort-Object @{Expression={(Bounds $_)[1]}})
            Check "Seat $seat has exactly two rendered score rows" "$pointsLine / $totalLine" ($lines.text-join' / ')
        }
    }
}
try{
    $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$text=$reader.ReadToEnd();$reader.Dispose()
        if($text-and($text|ConvertFrom-Json).active){throw 'Android is reserved by another owner.'}
        $lease=[ordered]@{active=$true;leaseId=$leaseId;serial='127.0.0.1:5555';owner='Rentz shared UI minimal sanity';run=$raw;acquiredUtc=[datetime]::UtcNow.ToString('o')}
        $json=$lease|ConvertTo-Json;$json|Set-Content -LiteralPath (Join-Path $raw 'lease.json') -Encoding utf8NoBOM
        $bytes=[Text.UTF8Encoding]::new($false).GetBytes($json);$stream.SetLength(0);$stream.Position=0;$stream.Write($bytes);$stream.Flush($true);$owned=$true
    }finally{$stream.Dispose()}
    Adb @('get-state') 'device'|Out-Null
    $power=(Adb @('shell','dumpsys','power') 'power-before').Output
    $wasAsleep=$power-match'mWakefulness=Asleep'
    Adb @('shell','input','keyevent','KEYCODE_WAKEUP') 'wake-device'|Out-Null
    Adb @('shell','wm','dismiss-keyguard') 'dismiss-emulator-keyguard'|Out-Null
    $initialSize=(Adb @('shell','wm','size') 'size').Output;$initialDensity=(Adb @('shell','wm','density') 'density').Output;$initialFont=(Adb @('shell','settings','get','system','font_scale') 'font').Output
    $api=(Adb @('shell','getprop','ro.build.version.sdk') 'api').Output.Trim()
    $activity=(Adb @('shell','dumpsys','activity','activities') 'foreground').Output
    $match=[regex]::Match($activity,'mResumedActivity:.*? ([a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+) ');if($match.Success){$initialActivity=$match.Groups[1].Value}
    $packagesBefore=((Adb @('shell','pm','path','ro.whistlab.app') 'whist-before' -Allowed @(0,1)).Output+(Adb @('shell','pm','path','ro.rentzlab.app') 'rentz-before' -Allowed @(0,1)).Output)
    $existing=Adb @('shell','pm','path','ro.rentzlab.app.uisanity') 'sanity-existing' -Allowed @(0,1)
    if($existing.Output-match'package:'){throw 'The isolated test package already exists; identity and data need reconciliation before reuse.'}
    Adb @('install',$build.apk) 'install'|Out-Null;$installed=$true
    Adb @('shell','am','start','-W','-n','ro.rentzlab.app.uisanity/ro.rentzlab.app.MainActivity') 'launch'|Out-Null
    if($HelpAwardsOnly){RunHelpAwardsSanity}elseif($HistoryOnly){RunHistorySanity}else{
    Observe 'English shared home and disabled resume' {param($nodes) $t=Text $nodes;Check 'Rentz home' $true ($t.Contains('Rentz'));Check 'English first launch' $true ($t.Contains('New game'));Check 'Resume disabled' 1 @($nodes|Where-Object{$_.text-eq'Resume game'-and$_.enabled-eq'false'}).Count}
    Tap 'Player: You';Tap 'Player' 'android.widget.EditText';$typing=[Diagnostics.Stopwatch]::StartNew();Adb @('shell','input','text','Ana') 'type-name'|Out-Null;$typing.Stop()
    $actions.Add([pscustomobject]@{operation='Enter Android text';control='Player';value='Ana';elapsedMillis=$typing.ElapsedMilliseconds})
    Tap 'Save'
    Tap 'Interface language';Tap 'Română'
    Observe 'Romanian menu keeps the edited profile' {param($nodes) $t=Text $nodes;Check 'Romanian route copy' $true ($t.Contains('Partidă nouă'));Check 'Own persisted name' $true ($t.Contains('Jucător: Ana'))}
    Tap 'Limba interfeței';Tap 'English';Tap 'Scores'
    Observe 'Empty scores have an explicit native state' {param($nodes) Check 'No fabricated result before a game' $true ((Text $nodes).Contains('No completed hands yet'))}
    Tap 'Close';Tap 'New game';Tap 'Opponents';Tap 'Normal'
    Observe 'Separate Rentz setup without page scrolling' {param($nodes) $t=Text $nodes;Check 'Real opponent selection' $true ($t.Contains('Normal'));Check 'Start action' $true ($t.Contains('Start game'))}
    $back=[Diagnostics.Stopwatch]::StartNew();Adb @('shell','input','keyevent','KEYCODE_BACK') 'legacy-back'|Out-Null;$back.Stop()
    $actions.Add([pscustomobject]@{operation='Android back key';control='Setup navigation';elapsedMillis=$back.ElapsedMilliseconds})
    Tap 'New game'
    $token=Start-DevelopmentExposure
    Tap 'Start game';$state=State
    $journal=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/session.json') 'development-journal'
    $journalFile=Join-Path $raw 'development-journal.json'
    [IO.File]::WriteAllText($journalFile,$journal.Output,[Text.UTF8Encoding]::new($false))
    Complete-DevelopmentExposure $journalFile $token
    Observe 'Hidden hand and real Rentz chooser' {param($nodes) Check 'Hand remains hidden' 0 @($state.view.hand).Count;Check 'Chooser phase' 'CHOOSE' $state.view.phase;TableChecks $nodes $state}
    $revision=$state.revision;Tap 'Show cards';$state=State -After $revision
    Observe 'Eight revealed cards on the shared table' {param($nodes) Check 'Eight own cards' 8 @($state.view.hand).Count;TableChecks $nodes $state}
    if($ConsecutiveSubgames){
        RunConsecutiveSubgames $state $journalFile
    }else{
    Tap 'Choose game';$revision=$state.revision;Tap 'Tricks';$state=State -After $revision
    $played=0
    while($state.view.phase-eq'PLAY'){
        if(@($state.view.legal).Count-eq0){throw 'Trick sanity expected a legal own card.'}
        $card=$state.view.legal[0];$rank=switch([int]$card.rank){11{'J'}12{'Q'}13{'K'}14{'A'}default{[string]$card.rank}}
        $label=$rank+@{CLUBS='♣';DIAMONDS='♦';HEARTS='♥';SPADES='♠'}[[string]$card.suit]
        $revision=$state.revision;Tap ('Play '+$label);$state=State -After $revision;$played++
        if($played-eq1){Observe 'First native play preserves seats and the remaining hand' {param($nodes) Check 'One own card consumed' 7 @($state.view.hand).Count;TableChecks $nodes $state}}
        if($played-eq7){Observe 'One remaining card stays centered in the hand' {param($nodes)
            Check 'One own card remains' 1 @($state.view.hand).Count;TableChecks $nodes $state
            $hand=@($nodes|Where-Object{$_.description-like'Your hand:*'})
            $card=@($nodes|Where-Object{$_.description-like'Play *'-or$_.description-like'*. Unavailable'})
            if($hand.Count-eq1-and$card.Count-eq1){$h=Bounds $hand[0];$c=Bounds $card[0];Check 'Single-card horizontal center' $true ([math]::Abs(($h[0]+$h[2])-($c[0]+$c[2]))-le2)}
        }}
        if($played-gt8){throw 'Sanity exceeded a single Rentz trick hand.'}
    }
    for($attempt=0;$attempt-lt5;$attempt++){if((Ui).text -contains 'Hand score'){break}}
    Observe 'One hand completed with negative Rentz scoring' {param($nodes) Check 'Eight explicit own plays' 8 $played;Check 'Deal completed' 'DEAL_END' $state.view.phase;Check 'Eight tricks at minus ten' -80 ($state.view.totals|Measure-Object -Sum).Sum;Check 'Hand score screen follows collection' $true ($nodes.text -contains 'Hand score')}
    $terminal=$state
    Tap 'Score history';$history=State -Screen 'HISTORY'
    Observe 'Confirmed hand and Rentz score history' {param($nodes) $t=Text $nodes;Check 'Completed sub-game' $true ($t.Contains('1 · Tricks'));Check 'Own exact delta and total' $true ($t.Contains('Ana. Δ '+$terminal.view.dealPoints[0]+'. Σ '+$terminal.view.totals[0]))}
    Tap 'Close';$state=State;Tap 'Main menu'
    Observe 'Menu resumes the existing game' {param($nodes) Check 'Resume enabled' 1 @($nodes|Where-Object{$_.text-eq'Resume game'-and$_.enabled-eq'true'}).Count}
    Adb @('shell','am','force-stop','ro.rentzlab.app.uisanity') 'restart-stop'|Out-Null
    Adb @('shell','am','start','-W','-n','ro.rentzlab.app.uisanity/ro.rentzlab.app.MainActivity') 'restart-start'|Out-Null
    Tap 'Resume game';Tap 'View table';$restored=State
    Observe 'Process restart keeps the confirmed Rentz hand' {param($nodes) Check 'Restored totals' ($terminal.view.totals-join',') ($restored.view.totals-join',');Check 'No replayed command' $terminal.revision $restored.revision;Check 'Same completed phase' 'DEAL_END' $restored.view.phase;Check 'Same opponents in seat order' ($terminal.opponents-join',') ($restored.opponents-join',');TableChecks $nodes $restored}
    Tap 'Options';Tap 'Score history';$null=State -Screen 'HISTORY'
    Observe 'Restored score history still contains the confirmed row' {param($nodes) $t=Text $nodes;Check 'History restored' $true ($t.Contains('1 · Tricks'));Check 'Restored player identity' $true ($t.Contains('Ana'))}
    if($CompactScores){RunCompactScores $terminal}
    if($SeatExtremes){
        Tap 'Close';$null=State
        foreach($seatCount in @(6,3)){
            Tap 'Options';Tap 'Main menu';Tap 'New game';Tap 'Players';Tap ([string]$seatCount)
            $token=Start-DevelopmentExposure
            Tap 'Start game';Tap 'Start';$state=State
            $journal=Adb @('exec-out','run-as','ro.rentzlab.app.uisanity','cat','files/session.json') ('development-'+$seatCount)
            $journalFile=Join-Path $raw ('development-journal-'+$seatCount+'.json')
            [IO.File]::WriteAllText($journalFile,$journal.Output,[Text.UTF8Encoding]::new($false))
            Complete-DevelopmentExposure $journalFile $token
            $revision=$state.revision;Tap 'Show cards';$state=State -After $revision
            Observe ('Display-only initialization with '+$seatCount+' seats') {param($nodes) Check 'Requested Rentz table size' $seatCount $state.view.players;Check 'Eight cards on the single hand row' 8 @($state.view.hand).Count;TableChecks $nodes $state}
        }
    }
    }
    }
}catch{
    $failure=$_.Exception.ToString()
    if($owned){try{Adb @('shell','logcat','-d','-t','150','-s','AndroidRuntime:E') 'failure-logcat'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}}
}finally{
    if($owned){
        if($displayChanged){
            try{Adb @('shell','wm','size',$restoreSize) 'restore-history-size'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}
            try{Adb @('shell','wm','density',$restoreDensity) 'restore-history-density'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}
            $fontCommand=if($restoreFont-eq'null'){@('shell','settings','delete','system','font_scale')}else{@('shell','settings','put','system','font_scale',$restoreFont)}
            try{Adb $fontCommand 'restore-history-font'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}
        }
        if($journalPushed){try{Adb @('shell','rm','-f',$remoteJournal) 'remove-history-fixture'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}}
        try{
            $remotes=[Collections.Generic.HashSet[string]]::new()
            $null=$remotes.Add('/sdcard/rentz-ui-'+$leaseId+'.xml')
            foreach($dump in Get-ChildItem -LiteralPath $raw -File -Filter '*-dump.txt'){
                foreach($match in [regex]::Matches([IO.File]::ReadAllText($dump.FullName),'/sdcard/rentz-checkpoint-[0-9]{2}-attempt-[0-9]{2}-[a-f0-9]{32}\.xml')){$null=$remotes.Add($match.Value)}
            }
            foreach($remote in $remotes){Adb @('shell','rm','-f',$remote) 'remove-owned-hierarchy'|Out-Null}
        }catch{$cleanup.Add($_.Exception.ToString())}
        if($installed){try{Adb @('shell','am','force-stop','ro.rentzlab.app.uisanity') 'finish-stop'|Out-Null;Adb @('uninstall','ro.rentzlab.app.uisanity') 'uninstall-owned'|Out-Null}catch{$cleanup.Add($_.Exception.ToString())}}
        try{
            $finalSize=(Adb @('shell','wm','size') 'final-size').Output;$finalDensity=(Adb @('shell','wm','density') 'final-density').Output;$finalFont=(Adb @('shell','settings','get','system','font_scale') 'final-font').Output
            $packagesAfter=((Adb @('shell','pm','path','ro.whistlab.app') 'whist-after' -Allowed @(0,1)).Output+(Adb @('shell','pm','path','ro.rentzlab.app') 'rentz-after' -Allowed @(0,1)).Output)
            if($initialActivity){Adb @('shell','am','start','-W','-n',$initialActivity) 'restore-foreground'|Out-Null}
            if($wasAsleep){Adb @('shell','input','keyevent','KEYCODE_SLEEP') 'restore-sleep'|Out-Null}
        }catch{$cleanup.Add($_.Exception.ToString())}
        $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        try{
            $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true);$current=$reader.ReadToEnd()|ConvertFrom-Json;$reader.Dispose()
            if($current.leaseId-eq$leaseId){$current.active=$false;$current|Add-Member -NotePropertyName releasedUtc -NotePropertyValue ([datetime]::UtcNow.ToString('o')) -Force;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($current|ConvertTo-Json));$stream.SetLength(0);$stream.Position=0;$stream.Write($bytes);$stream.Flush($true);$released=$true}
        }finally{$stream.Dispose()}
    }
}
$preserved=$initialSize-and$initialSize-eq$finalSize-and$initialDensity-eq$finalDensity-and$initialFont-eq$finalFont-and$packagesBefore-ceq$packagesAfter
$sharedDevelopmentLedgerSha256After='';$sharedDevelopmentLedgerUnchanged=$false;$isolatedDevelopmentLedgerSha256After=''
if(-not $HelpAwardsOnly){
    $sharedDevelopmentLedgerSha256After=(Get-FileHash -LiteralPath $sharedDevelopmentLedger -Algorithm SHA256).Hash.ToLowerInvariant()
    $sharedDevelopmentLedgerUnchanged=$sharedDevelopmentLedgerSha256Before-eq$sharedDevelopmentLedgerSha256After
    if(Test-Path -LiteralPath $developmentLedger -PathType Leaf){$isolatedDevelopmentLedgerSha256After=(Get-FileHash -LiteralPath $developmentLedger -Algorithm SHA256).Hash.ToLowerInvariant()}
    if($ConsecutiveSubgames-and-not$sharedDevelopmentLedgerUnchanged-and-not$failure){$failure='Shared training ledger changed during isolated native acceptance.'}
}
$minimum=if($HelpAwardsOnly){3}elseif($HistoryMatrix){10}elseif($HistoryOnly){5}elseif($ConsecutiveSubgames){16}else{10}
if($HistoryDetails){$minimum+=4}
if($CompactScores){$minimum++}
if(-not$failure-and($cleanup.Count-or-not$released-or-not$preserved-or$points.Count-lt$minimum)){$failure='Native closeout failed or the declared checkpoint population is incomplete.'}
$scope=if($HelpAwardsOnly){'Rentz help finishing awards for 3–6 players in English and Romanian, with no match started'}elseif($HistoryMatrix){'Adaptive Rentz history with 1.5 text at 720x1280 and 720x740, six players and 48 confirmed hands, including recovery after resize'}elseif($HistoryOnly){'Adaptive Rentz history on a replayed 48-hand journal: empty, first row, last player, final row, return to menu; one native display profile'}elseif($ConsecutiveSubgames){'Three consecutive complete UI games in one match: full Rentz followed by two non-Rentz games, each to DEAL_END; four-seat geometry, score history, restart and independent journal replay'}else{'Shared UI integration: one trick hand, menu, profile, languages, history and restart; optional display-only 3/6 seats; one native display profile'}
if($HistoryDetails){$scope+='; confirmed blind trick contributions and the actual Rentz finishing places after journal restore'}
if($CompactScores){$scope+='; Romanian compact signed scores at font scale 1.5 and a 720x1280 logical viewport'}
$display=[ordered]@{before=@{size=$initialSize.Trim();density=$initialDensity.Trim();font=$initialFont.Trim()};after=@{size=$finalSize.Trim();density=$finalDensity.Trim();font=$finalFont.Trim()}}
if($component){
    $reportInput=[ordered]@{status=$(if($failure){'FAIL'}else{'PASS'});scope=$scope;consecutiveSubgames=[bool]$ConsecutiveSubgames;package='ro.rentzlab.app.uisanity';serial='127.0.0.1:5555';versionName=$build.versionName;api=$api;apkSha256=$build.apkSha256;failure=$failure;display=$display;displayAndPackagesPreserved=[bool]$preserved;leaseReleased=$released;subgames=$threeSubgameContracts;completedGames=$completedGames.ToArray();gameAudit=$threeSubgameOracle;developmentLedger=$developmentLedger;developmentLedgerScope=$(if($HelpAwardsOnly){'NOT_USED'}else{'RUN_ISOLATED_COPY'});sharedDevelopmentLedgerSha256Before=$sharedDevelopmentLedgerSha256Before;sharedDevelopmentLedgerSha256After=$sharedDevelopmentLedgerSha256After;sharedDevelopmentLedgerUnchanged=$sharedDevelopmentLedgerUnchanged;isolatedDevelopmentLedgerSha256Before=$isolatedDevelopmentLedgerSha256Before;isolatedDevelopmentLedgerSha256After=$isolatedDevelopmentLedgerSha256After;checkpoints=$points.ToArray()}
    $reportInput|ConvertTo-Json -Depth 16|Set-Content -LiteralPath (Join-Path $raw 'ui-result.json') -Encoding utf8NoBOM
    ConvertTo-Json -InputObject @($points.ToArray()) -Depth 16|Set-Content -LiteralPath (Join-Path $raw 'checkpoints.json') -Encoding utf8NoBOM
    if($points.Count){
        try{
            Invoke-DevelopmentExposure @('report',$raw)
            $sourceExtent=Join-Path $raw 'android-extent.html'
            if(-not(Test-Path -LiteralPath $sourceExtent -PathType Leaf)){throw 'atf-reporting did not create the native Extent artifact.'}
            $extentPath=Join-Path $RunRoot 'android-extent.html'
            Copy-Item -LiteralPath $sourceExtent -Destination $extentPath
        }catch{if(-not$failure){$failure=$_.Exception.Message}}
    }
}
$extentHash='';if($extentPath-and(Test-Path -LiteralPath $extentPath -PathType Leaf)){$extentHash=(Get-FileHash -LiteralPath $extentPath -Algorithm SHA256).Hash.ToLowerInvariant()}
$result=[ordered]@{status=$(if($failure){'FAIL'}else{'PASS'});scope=$scope;helpAwardsOnly=[bool]$HelpAwardsOnly;historyOnly=[bool]$HistoryOnly;historyMatrix=[bool]$HistoryMatrix;historyDetails=[bool]$HistoryDetails;seatExtremes=[bool]$SeatExtremes;consecutiveSubgames=[bool]$ConsecutiveSubgames;subgames=$threeSubgameContracts;completedGames=$completedGames.ToArray();gameAudit=$threeSubgameOracle;package='ro.rentzlab.app.uisanity';serial='127.0.0.1:5555';versionName=$build.versionName;api=$api;apkSha256=$build.apkSha256;build=$build;rawRoot=$raw;extentReport=$extentPath;extentReportSha256=$extentHash;reportingLibrary=$reportingLibrary.name;reportingLibrarySha256=$reportingLibrary.sha256;runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant();developmentLedger=$developmentLedger;developmentLedgerScope=$(if($HelpAwardsOnly){'NOT_USED'}else{'RUN_ISOLATED_COPY'});isolatedDevelopmentLedgerSha256Before=$isolatedDevelopmentLedgerSha256Before;isolatedDevelopmentLedgerSha256After=$isolatedDevelopmentLedgerSha256After;sharedDevelopmentLedger=$sharedDevelopmentLedger;sharedDevelopmentLedgerSha256Before=$sharedDevelopmentLedgerSha256Before;sharedDevelopmentLedgerSha256After=$sharedDevelopmentLedgerSha256After;sharedDevelopmentLedgerUnchanged=$sharedDevelopmentLedgerUnchanged;developmentExposures=$developmentExposures.ToArray();failure=$failure;cleanupFailures=$cleanup.ToArray();display=$display;displayAndPackagesPreserved=[bool]$preserved;leaseReleased=$released;checkpoints=$points.ToArray()}
$result|ConvertTo-Json -Depth 20|Set-Content -LiteralPath (Join-Path $RunRoot 'result.json') -Encoding utf8NoBOM
if($failure){throw "Shared UI sanity failed: $RunRoot"}
Write-Output $RunRoot
