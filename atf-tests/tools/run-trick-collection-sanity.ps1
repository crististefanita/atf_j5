param(
    [Parameter(Mandatory)][ValidateSet('Whist','Rentz','Seats')][string]$Mode,
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$RentzFixture,
    [string]$ExposureReceipt
)
$ErrorActionPreference='Stop'
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
Import-Module (Join-Path $project 'scripts/InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $project 'rentz-reference/tools/RentzAndroid.psm1') -Force
Assert-AtfArtifactPath -ProjectRoot $project -Path $RunRoot
if(Test-Path -LiteralPath $RunRoot){throw 'A fresh capture directory is required.'}
$apkPath=(Resolve-Path -LiteralPath $Apk).Path
$exposure=$null
if($Mode-eq'Rentz'){
    $preFinal=Join-Path (Resolve-Path -LiteralPath $RentzFixture).Path 'pre-final.json'
    Assert-AtfArtifactPath -ProjectRoot $project -Path $ExposureReceipt
    $exposure=Get-Content -LiteralPath $ExposureReceipt -Raw|ConvertFrom-Json
    if($exposure.token-cnotmatch'^[a-f0-9]{32}$'-or(Get-FileHash -LiteralPath $preFinal).Hash-ne$exposure.sha256){
        throw 'The exact Rentz fixture must be registered as development exposure before the device run.'
    }
}
$null=New-Item -ItemType Directory -Path $RunRoot
$leasePath='C:\work\whist_back\android-ui-lease.json'
$leaseId=[guid]::NewGuid().ToString('N')
$lease=[ordered]@{active=$true;leaseId=$leaseId;serial='127.0.0.1:5555';owner="Collection frames: $Mode";run=$RunRoot;acquiredUtc=[datetime]::UtcNow.ToString('o')}
$package=switch($Mode){'Whist'{'ro.whistlab.app.motioncheck'}'Rentz'{'ro.rentzlab.app.uisanity'}'Seats'{'ro.cardtable.ui.test'}}
$component=switch($Mode){'Whist'{'ro.whistlab.app.MainActivity'}'Rentz'{'ro.rentzlab.app.MainActivity'}}
$remote="/data/local/tmp/atf-collection-$leaseId"
$captures=[Collections.Generic.List[object]]::new()
$features=[Collections.Generic.List[object]]::new()
$ordinal=0;$installed=$false;$owned=$false;$fixturePushed=$false;$previous='';$failure=''

function Adb([string[]]$Arguments,[string]$Label,[switch]$Binary,[int]$Timeout=45,[int[]]$Allowed=@(0)){
    $script:ordinal++
    Invoke-RentzAdb -RunRoot $RunRoot -Arguments $Arguments -Label ('{0:d3}-{1}' -f $script:ordinal,$Label) -Binary:$Binary -TimeoutSeconds $Timeout -AllowedExitCodes $Allowed
}
function Ui {
    $remoteXml="$remote.xml"
    for($attempt=0;$attempt-lt3;$attempt++){
        $dump=Adb @('shell','uiautomator','dump',$remoteXml) 'dump'
        if(($dump.Output+$dump.Error)-notmatch'ERROR|Exception' -and $dump.Output.Contains($remoteXml)){
            $tree=Adb @('exec-out','cat',$remoteXml) 'hierarchy'
            return [pscustomobject]@{file=$tree.File;nodes=@(([xml]$tree.Output).SelectNodes('//node'))}
        }
    }
    throw 'Fresh Android hierarchy unavailable.'
}
function Box($node) {
    $m=[regex]::Match([string]$node.bounds,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if(!$m.Success){throw 'Invalid Android bounds'}
    @($m.Groups | Select-Object -Skip 1 | ForEach-Object {[int]$_.Value})
}
function Control([string]$Pattern){
    for($attempt=0;$attempt-lt8;$attempt++){
        $tree=Ui
        $nodes=@($tree.nodes|Where-Object { $_.package -eq $package -and $_.enabled -eq 'true' -and
                $_.clickable -eq 'true' -and ($_.text -match $Pattern -or $_.'content-desc' -match $Pattern) })
        if($nodes.Count-eq1){return $nodes[0]}
        if($nodes.Count-gt1){throw "Ambiguous native control $Pattern"}
        Start-Sleep -Milliseconds 200
    }
    throw "Native control unavailable: $Pattern"
}
function Tap($node,[string]$Label){
    $box=Box $node
    $watch=[Diagnostics.Stopwatch]::StartNew()
    Adb @('shell','input','tap',[string][int](($box[0]+$box[2])/2),[string][int](($box[1]+$box[3])/2)) 'tap'|Out-Null
    [ordered]@{operation='Tap Android control';control=$Label;bounds=$node.bounds;elapsedMillis=$watch.ElapsedMilliseconds}
}
function SaveFeature([string]$Name,$tree,$checks,[object[]]$actions=@()){
    $capture=Adb @('exec-out','screencap','-p') 'feature-screen' -Binary
    $image=Join-Path $RunRoot "$Name.png"
    Copy-Item -LiteralPath $capture.File -Destination $image
    $features.Add([ordered]@{name=$Name;image=$image;hierarchy=$tree.file;checks=@($checks);actions=$actions})
    if(@($checks|Where-Object{-not$_.passed}).Count){throw "Feature assertions failed: $Name"}
}
function Check([string]$Name,$Expected,$Actual){
    [ordered]@{name=$Name;expected=[string]$Expected;actual=[string]$Actual;passed=([string]$Expected-ceq[string]$Actual)}
}
function InstallRentzJournal([string]$Journal) {
    Adb @('push',$Journal,"$remote.json") 'push-fixture'|Out-Null;$script:fixturePushed=$true
    Adb @('shell','run-as',$package,'mkdir','-p','files') 'prepare-journal'|Out-Null
    Adb @('shell','run-as',$package,'cp',"$remote.json",'files/session.json') 'install-journal'|Out-Null
}
function VerifyRentzFeatures {
    $tree=Ui
    $labels=@($tree.nodes|ForEach-Object text)
    SaveFeature 'score-page' $tree @(
        (Check 'The final trick hands off to its own score page' 1 @($labels|Where-Object {$_-eq'Hand score'}).Count),
        (Check 'The current contract and its ×2 multiplier are visible' $true ($labels -contains 'Hand 1/32 · Whist ×2')),
        (Check 'The score includes the next-hand control' $true ($labels -contains 'Next hand')),
        (Check 'All four players show hand and running totals' 4 @($labels | Where-Object {$_ -match '^Δ -?\d+\s+·\s+Σ -?\d+$'}).Count),
        (Check 'All four players show their scored contribution' 4 @($labels | Where-Object {$_ -match '^Whist × \d+\s+·\s+-?\d+$'}).Count))
    $viewTable=Tap (Control '^View table$') 'Open the settled table from its score page'
    $tree=Ui
    $frames=@($tree.nodes|Where-Object {$_.'content-desc' -match '\. Points: -?\d+\. Total: -?\d+'})
    SaveFeature 'symbolic-table' $tree @(
        (Check 'Four accessible seats keep full score labels' 4 $frames.Count),
        (Check 'One visible delta line per seat' 4 @($tree.nodes|Where-Object {$_.text-match'^Δ -?\d+$'}).Count),
        (Check 'One visible sum line per seat' 4 @($tree.nodes|Where-Object {$_.text-match'^Σ -?\d+$'}).Count)) @($viewTable)
    Adb @('shell','am','force-stop',$package) 'stop-before-choice'|Out-Null
    $chooser=Join-Path $RentzFixture 'chooser.json'
    $initial=(Get-Content -LiteralPath $preFinal -Raw)|ConvertFrom-Json
    $choice=(Get-Content -LiteralPath $chooser -Raw)|ConvertFrom-Json
    if($initial.seed-ne$choice.seed-or$initial.players-ne$choice.players){throw 'Choice fixture is outside the registered distribution'}
    InstallRentzJournal $chooser
    Adb @('shell','am','start','-W','-n',"$package/$component") 'launch-choice'|Out-Null
    $enter=Tap (Control '^Resume game$') 'Open the saved choice before reveal'
    $blind=Tap (Control '^Choose blind ×2$') 'Open the blind choice'
    $tree=Ui
    SaveFeature 'blind-choice' $tree @(
        (Check 'Blind choice remains available' $true ($tree.nodes.text -contains 'Choose game ×2')),
        (Check 'A hidden hand exposes no AI suggestion control' $false ($tree.nodes.text -contains 'AI hint'))) @($enter,$blind)
    $cancel=Tap (Control '^Cancel$') 'Close the blind choice'
    $reveal=Tap (Control '^Show cards$') 'Reveal the human hand'
    $picker=Tap (Control '^Choose game$') 'Open the revealed choice'
    $beforeHint=ReadJournal 'before-hint'
    $hint=Tap (Control '^AI hint$') 'Ask for AI advice'
    $advice=''
    for($attempt=0;$attempt-lt5;$attempt++){
        $tree=Ui
        $advice=@($tree.nodes|ForEach-Object text|Where-Object {$_-match'^AI suggests: .+'}|Select-Object -First 1)
        if($advice.Count){break}
    }
    $suggested=if($advice.Count){$advice[0].Substring('AI suggests: '.Length)}else{''}
    $afterHint=ReadJournal 'after-hint'
    SaveFeature 'ai-hint' $tree @(
        (Check 'The displayed suggestion is also a selectable game' $true ($suggested -ne '' -and $tree.nodes.text -contains $suggested)),
        (Check 'Advice does not apply any command' $true ($beforeHint-ceq$afterHint))) @($cancel,$reveal,$picker,$hint)
}
function Record([string]$Name,$node,[string]$Label){
    $capture="$remote-$Name.mp4"
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName='C:\tools\whist-android-sdk\platform-tools\adb.exe'
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true
    $info.Environment['ADB_SERVER_SOCKET']='tcp:192.168.56.1:5038'
    $info.RedirectStandardError=$true
    foreach($argument in @('-s',$lease.serial,'shell','screenrecord','--size','360x640','--bit-rate','5M','--time-limit','8',$capture)){
        $info.ArgumentList.Add($argument)
    }
    $process=[Diagnostics.Process]::Start($info)
    try{
        $errors=$process.StandardError.ReadToEndAsync()
        Start-Sleep -Milliseconds 850
        if($process.HasExited){throw "Android recording failed: $($errors.GetAwaiter().GetResult())"}
        $action=Tap $node $Label
        if(!$process.WaitForExit(13000)-or$process.ExitCode){throw 'Android recording did not complete'}
        $file=Join-Path $RunRoot "$Name.mp4"
        Adb @('pull',$capture,$file) 'pull-video'|Out-Null
        $tree=Ui
        $screen=Adb @('exec-out','screencap','-p') 'settled-screen' -Binary
        $image=Join-Path $RunRoot "$Name-settled.png"
        Copy-Item -LiteralPath $screen.File -Destination $image
        $captures.Add([ordered]@{name=$Name;video=$file;sha256=(Get-FileHash -LiteralPath $file).Hash;image=$image;hierarchy=$tree.file;actions=@($action)})
    } finally {
        if(!$process.HasExited){$process.Kill();$process.WaitForExit()}
        $process.Dispose()
        Adb @('shell','rm','-f',$capture) 'remove-device-video'|Out-Null
    }
}
function ReadJournal([string]$Label){
    if($Mode-eq'Rentz'){
        return (Adb @('exec-out','run-as',$package,'cat','files/session.json') $Label).Output
    }
    $prefs=(Adb @('exec-out','run-as',$package,'cat','shared_prefs/whist_lab_game.xml') $Label).Output
    return ([xml]$prefs).SelectSingleNode("//string[@name='current_session_v1']").InnerText
}
function SeatBounds($tree,$name){
    $seat=@($tree.nodes|Where-Object { $_.'content-desc' -match ('^'+[regex]::Escape($name)+'\. (Bid|Points)[: ]') })
    if($seat.Count-ne1){throw "Expected one displayed seat for $name"}
    Box $seat[0]
}
try {
    $stream=[IO.File]::Open($leasePath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try {
        $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true)
        $content=$reader.ReadToEnd();$reader.Dispose()
        if($content-and($content|ConvertFrom-Json).active){throw 'Android is reserved by another owner.'}
        $json=$lease|ConvertTo-Json;$bytes=[Text.UTF8Encoding]::new($false).GetBytes($json)
        [IO.File]::WriteAllText((Join-Path $RunRoot 'lease.json'),$json)
        $stream.SetLength(0);$stream.Position=0;$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true);$owned=$true
    } finally {$stream.Dispose()}
    $api=(Adb @('shell','getprop','ro.build.version.sdk') 'api').Output.Trim()
    $screen=(Adb @('shell','wm','size') 'display-size').Output
    $display=[regex]::Matches($screen,'(\d+)x(\d+)')[-1]
    $width=[int]$display.Groups[1].Value;$height=[int]$display.Groups[2].Value
    if($width*640-ne$height*360){throw 'This frame probe requires a 9:16 portrait display.'}
    $activity=(Adb @('shell','dumpsys','activity','activities') 'original-activity').Output
    $previous=[regex]::Match($activity,'mResumedActivity:.*? ([a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+) ').Groups[1].Value
    $power=(Adb @('shell','dumpsys','power') 'original-power').Output
    $asleep=$power-match'mWakefulness=Asleep'
    $present=Adb @('shell','pm','path',$package) 'original-package' -Allowed @(0,1)
    if($present.Output-match'package:'){throw 'The isolated package must be absent before capture.'}
    Adb @('shell','input','keyevent','KEYCODE_WAKEUP') 'wake'|Out-Null
    Adb @('shell','wm','dismiss-keyguard') 'dismiss-keyguard'|Out-Null
    Adb @('install',$apkPath) 'install'|Out-Null;$installed=$true
    if($Mode-eq'Seats'){
        $result=Adb @('shell','am','instrument','-w',"$package/ro.cardtable.ui.seat.SeatFrameComponentInstrumentation") 'native-components' -Timeout 80
        $match=[regex]::Match($result.Output,'(?m)^ATF_NATIVE_SEATS=(\{.*\})\r?$')
        if(!$match.Success){throw "Native seat result missing: $($result.Output)"}
        $result=$match.Groups[1].Value|ConvertFrom-Json
        $result|ConvertTo-Json -Depth 18|Set-Content -LiteralPath (Join-Path $RunRoot 'seats.json') -Encoding utf8NoBOM
        if($result.failures-or$result.error-or$result.cases.Count-ne36){throw 'Native seat component matrix failed.'}
    } else {
        if($Mode-eq'Whist'){
            Adb @('shell','am','start','-W','-n',"$package/$component",'--ez','debug_fixture','true','--ez','persist_fixture','true') 'start-whist-fixture'|Out-Null
            Tap (Control '^(0|Bid 0(?:, selected)?)$') 'Select zero bid'|Out-Null
            Tap (Control '^Place bid$') 'Confirm zero bid'|Out-Null
            $lastCard=Control '^Ace of Hearts\.'
            $winner=0;$name='You'
        } else {
            Copy-Item -LiteralPath $preFinal -Destination (Join-Path $RunRoot 'development-journal.json')
            InstallRentzJournal $preFinal
            Adb @('shell','am','start','-W','-n',"$package/$component") 'start-rentz-fixture'|Out-Null
            Tap (Control '^Resume game$') 'Open the pre-final checkpoint'|Out-Null
            $lastCard=Control '^Play (?:[2-9]|10|[JQKA])[♣♦♥♠]$'
            $winner=[int](Get-Content (Join-Path $RentzFixture 'winner.txt') -Raw)
            $state=(Adb @('exec-out','run-as',$package,'cat','files/actor-view.json') 'prefinal-actor').Output|ConvertFrom-Json
            $name=if($winner-eq0){'You'}else{$state.opponents[$winner-1]}
        }
        $winnerBox=SeatBounds (Ui) $name
        Record 'live' $lastCard 'Play the last legal card'
        $before=ReadJournal 'saved-before-restart'
        $saved=$before|ConvertFrom-Json
        if($Mode-eq'Whist'-and$saved.phase-ne'SCORE'){throw 'Whist final score was not saved'}
        if($Mode-eq'Rentz'){
            $actor=(Adb @('exec-out','run-as',$package,'cat','files/actor-view.json') 'final-actor').Output|ConvertFrom-Json
            if($actor.view.phase-ne'DEAL_END'){throw 'Rentz final score was not reached'}
            $events=@($actor.view.events | Where-Object action -eq 'PLAY')
            $last=@($events | Select-Object -Last 4)
            $actualWinner=($last|Where-Object {$_.card.suit-eq$last[0].card.suit}|Sort-Object {$_.card.rank} -Descending|Select-Object -First 1).seat
            if($actualWinner-ne$winner){throw 'The final public trick has a different winner'}
        } elseif($saved.lastTrickWinnerSeat-ne$winner){throw 'Whist fixture has a different winning seat'}
        Adb @('shell','am','force-stop',$package) 'cold-stop'|Out-Null
        Adb @('shell','am','start','-W','-n',"$package/$component") 'cold-start'|Out-Null
        Record 'restored' (Control '^Resume game$') 'Resume the saved hand after force-stop'
        $after=ReadJournal 'saved-after-restart'
        if($before-cne$after){throw 'The game checkpoint changed after visual replay'}
        if($Mode-eq'Rentz'){VerifyRentzFeatures}
    }
} catch {$failure=$_.Exception.Message;throw}
finally {
    if($owned){
        try {
            if($installed){Adb @('uninstall',$package) 'uninstall-isolated'|Out-Null}
            if($fixturePushed){Adb @('shell','rm','-f',"$remote.json") 'remove-fixture'|Out-Null}
            Adb @('shell','rm','-f',"$remote.xml") 'remove-hierarchy'|Out-Null
            if($previous){Adb @('shell','am','start','-W','-n',$previous) 'restore-activity'|Out-Null}
            if($asleep){Adb @('shell','input','keyevent','KEYCODE_SLEEP') 'restore-power'|Out-Null}
        } finally {
            $stream=[IO.File]::Open($leasePath,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
            try {
                $reader=[IO.StreamReader]::new($stream,[Text.Encoding]::UTF8,$true,1024,$true)
                $current=$reader.ReadToEnd()|ConvertFrom-Json;$reader.Dispose()
                if($current.leaseId-ne$leaseId){throw 'The lease changed before closeout'}
                $lease.active=$false;$lease.releasedUtc=[datetime]::UtcNow.ToString('o')
                $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($lease|ConvertTo-Json))
                $stream.SetLength(0);$stream.Position=0;$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)
            } finally {$stream.Dispose()}
        }
    }
    [ordered]@{mode=$Mode;api=$api;apkSha256=(Get-FileHash -LiteralPath $apkPath).Hash;display=@($width,$height);
        winner=$winner;winnerName=$name;winnerBounds=$winnerBox;checkpointUnchanged=($Mode-ne'Seats'-and$before-and$before-ceq$after);
        captures=$captures.ToArray();features=$features.ToArray();developmentExposure=$exposure;failure=$failure;leaseReleased=($owned-and-not$lease.active)} |
        ConvertTo-Json -Depth 12|Set-Content -LiteralPath (Join-Path $RunRoot 'capture.json') -Encoding utf8NoBOM
}
