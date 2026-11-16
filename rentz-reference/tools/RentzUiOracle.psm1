function Wait-RentzUiHierarchy {
    param(
        [Parameter(Mandatory)][scriptblock]$ReadHierarchy,
        [Parameter(Mandatory)][scriptblock]$Recover,
        [Parameter(Mandatory)][datetime]$Deadline,
        [scriptblock]$Pause = { param($Milliseconds) Start-Sleep -Milliseconds $Milliseconds }
    )
    $interrupted = $false
    for($attempt=0;$attempt-lt8-and[datetime]::UtcNow-lt$Deadline;$attempt++){
        $frame = & $ReadHierarchy
        if($null-eq$frame){& $Pause 300; continue}
        if(& $Recover $frame){$interrupted=$true; continue}
        return $frame.nodes
    }
    if($interrupted){throw 'ANDROID_SYSTEM_UI_ANR: no fresh application hierarchy within the observation budget.'}
    throw 'Fresh native hierarchy unavailable within the observation retry budget.'
}

function Wait-RentzActorCheckpoint {
    param(
        [Parameter(Mandatory)][scriptblock]$ReadActor,
        [long]$After,
        [string]$Screen,
        [datetime]$Deadline
    )
    do{
        $payload = & $ReadActor
        if($null-ne$payload){
            try{
                $state = $payload | ConvertFrom-Json -ErrorAction Stop
                if($state.revision-gt$After-and$state.screen-eq$Screen-and-not$state.busy){return $state}
            }catch{}
        }
        Start-Sleep -Milliseconds 200
    }while([datetime]::UtcNow-lt$Deadline)
    throw 'Current actor checkpoint did not reach the expected revision and route.'
}

function Wait-RentzVisualCheckpoint {
    param(
        [Parameter(Mandatory)][scriptblock]$Capture,
        [Parameter(Mandatory)][scriptblock]$Recover,
        [Parameter(Mandatory)][datetime]$Deadline,
        [string]$UntilText='',
        [scriptblock]$Clock = { [datetime]::UtcNow },
        [scriptblock]$Pause = { param($Milliseconds) Start-Sleep -Milliseconds $Milliseconds }
    )
    $attempt=0
    $lastFailure=$null
    while((& $Clock)-lt$Deadline){
        $attempt++
        try{$frame = & $Capture $attempt}
        catch{
            if($_.Exception.Message-notlike'Fresh hierarchy was not confirmed*'){throw}
            $lastFailure=$_.Exception
            & $Pause 250
            continue
        }
        if(& $Recover $frame){
            $lastFailure=[InvalidOperationException]::new("ANDROID_SYSTEM_UI_ANR: no fresh application frame after $($frame.name).")
            continue
        }
        $lastFailure=$null
        if(!$UntilText-or$frame.nodes.text-ccontains$UntilText){
            if((& $Clock)-lt$Deadline){return $frame}
            break
        }
        & $Pause 250
    }
    if($lastFailure){throw $lastFailure}
    if($UntilText){throw "Native observation did not reach required text: $UntilText"}
    throw 'Fresh native hierarchy unavailable within the observation retry budget.'
}

function Get-RentzTapTarget {
    param([Parameter(Mandatory)][System.Xml.XmlElement[]]$Nodes,[Parameter(Mandatory)][string]$Label,[string]$Class='')
    $matches=@(foreach($node in $Nodes){
        $listItem=$node.class-in@('android.widget.CheckedTextView','android.widget.TextView')-and
            $null-ne$node.SelectSingleNode("ancestor::node[@class='android.widget.ListView']")
        if(($node.text-ieq$Label-or$node.'content-desc'-ceq$Label)-and$node.enabled-eq'true'-and
           ($node.clickable-eq'true'-or$listItem)-and(-not$Class-or$node.class-eq$Class)){$node}
    })
    if($matches.Count-gt1){throw "Ambiguous native control: $Label"}
    if($matches.Count-eq1){return $matches[0]}
    return $null
}

function Get-RentzSystemUiWaitTarget {
    param([Parameter(Mandatory)][System.Xml.XmlElement[]]$Nodes)
    $title=@($Nodes|Where-Object {$_.package-ceq'android'-and$_.'resource-id'-ceq'android:id/alertTitle'-and
        $_.text-ceq"System UI isn't responding"})
    if(!$title.Count){return $null}
    $wait=@($title[0].SelectNodes('ancestor::node[.//node[@resource-id="android:id/aerr_wait"]][1]//node[@resource-id="android:id/aerr_wait"]')|
        Where-Object {$_.package-ceq'android'-and$_.class-ceq'android.widget.Button'-and$_.enabled-eq'true'-and$_.clickable-eq'true'})
    if($title.Count-ne1-or$wait.Count-ne1){throw 'ANDROID_SYSTEM_UI_ANR: no unambiguous system Wait action.'}
    return $wait[0]
}


function Start-RentzNextDeal {
    param(
        [Parameter(Mandatory)]$Completed,
        [Parameter(Mandatory)][scriptblock]$Advance,
        [Parameter(Mandatory)][scriptblock]$ReadActor,
        [Parameter(Mandatory)][scriptblock]$ChooseHuman
    )
    if($Completed.view.phase-cne'DEAL_END'){
        throw 'ANDROID-NEXT-DEAL: previous hand is not settled.'
    }
    $expectedHand = $Completed.view.handNumber + 1
    $after = $Completed.revision
    $null = & $Advance
    $state = & $ReadActor $after
    for($choice=0;$choice-le8;$choice++){
        if($null-eq$state-or$state.revision-le$after-or$state.busy-ne$false){
            throw 'ANDROID-NEXT-DEAL: stale or busy actor observation.'
        }
        if($state.screen-cne'GAME'){
            throw 'ANDROID-NEXT-DEAL: expected the game route.'
        }
        if($state.view.handNumber-ne$expectedHand){
            throw 'ANDROID-NEXT-DEAL: wrong hand number.'
        }
        if($state.view.phase-ceq'PLAY'){return $state}
        if($state.view.phase-cne'CHOOSE'){
            throw 'ANDROID-NEXT-DEAL: expected CHOOSE or PLAY.'
        }
        if($choice-eq8){break}
        $after = $state.revision
        if($state.view.turn-eq0){$state = & $ChooseHuman $state}
        else{$state = & $ReadActor $after}
    }
    throw 'ANDROID-NEXT-DEAL: chooser did not resolve.'
}

function Get-RentzCompletedDealChecks {
    param([Parameter(Mandatory)]$Actor,[int]$Hand,[string]$Contract)
    $view = $Actor.view
    $checks = @(
        @{name='Completed deal phase';expected='DEAL_END';actual=$view.phase}
        @{name='Exact completed hand number';expected=$Hand;actual=$view.handNumber}
        @{name='Completed subgame contract';expected=$Contract;actual=$view.contract}
        @{name='One score value per player';expected=$view.players;actual=$view.dealPoints.Count}
    )
    if($Contract-ceq'RENTZ'){
        $checks += @{name='Rentz finishing order contains each player';expected=(0..($view.players-1) -join ',');actual=(@($view.finishOrder|Sort-Object)-join ',')}
    }
    foreach($check in $checks){
        $check.expected = [string]$check.expected
        $check.actual = [string]$check.actual
        $check.passed = $check.expected-ceq$check.actual
        [pscustomobject]$check
    }
}

function Read-RentzVisibleBounds([string]$Value){
    $bounds=[regex]::Match($Value,'^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if($bounds.Success){
        $box=@(1..4|ForEach-Object {[int]$bounds.Groups[$_].Value})
        if($box[0]-lt$box[2]-and$box[1]-lt$box[3]){return $box}
    }
    throw "Invalid visible bounds: $Value"
}

function Get-RentzSeatFrames {
    param([object[]]$Nodes,[string[]]$Names)
    $frames=[Collections.Generic.List[object]]::new()
    foreach($name in $Names){
        $matches=@($Nodes|Where-Object {
            $label=if($_ -is [Xml.XmlElement]){$_.GetAttribute('content-desc')}else{[string]$_.description}
            $label.StartsWith($name+'. Points:',[StringComparison]::Ordinal)
        })
        if($matches.Count-ne1){throw "One visible frame required for seat $($frames.Count) ($name)."}
        $bounds=[string]$matches[0].bounds
        $box=@(Read-RentzVisibleBounds $bounds)
        foreach($previous in $frames){
            $other=$previous.box
            if($box[0]-lt$other[2]-and$box[2]-gt$other[0]-and$box[1]-lt$other[3]-and$box[3]-gt$other[1]){
                throw "Player frames intersect: $($previous.name) and $name"
            }
        }
        $frames.Add([pscustomobject]@{name=$name;bounds=$bounds;box=$box})
    }
    return $frames.ToArray()
}

function Get-RentzTurnBadgeChecks {
    param([object[]]$Nodes,[object[]]$Frames,[int]$Turn,[string]$Label)
    if($Turn-lt0-or$Turn-ge$Frames.Count){throw "Invalid badge actor seat: $Turn"}
    $badges=@($Nodes|Where-Object {$_.text -cin @('CHOOSING','PLAYING','PASSING')})
    $text=$badges.text -join ','
    $belongs=$false
    if($badges.Count-eq1){
        $badge=@(Read-RentzVisibleBounds $badges[0].bounds)
        $actor=$Frames[$Turn].box
        $belongs=$badge[0]+$badge[2]-eq$actor[0]+$actor[2]-and$badge[3]-le$actor[1]
    }
    [pscustomobject]@{name='One native action badge';expected='1';actual=[string]$badges.Count;passed=$badges.Count-eq1}
    [pscustomobject]@{name='Action badge label';expected=$Label;actual=$text;passed=$text-ceq$Label}
    [pscustomobject]@{name='Action badge centered above the active player';expected=$Frames[$Turn].name;actual=$(if($belongs){$Frames[$Turn].name}else{'outside the actor frame'});passed=$belongs}
}


function Get-RentzHistoryPageChecks {
    param(
        [AllowEmptyCollection()][AllowEmptyString()][string[]]$Labels,
        [string[]]$Contracts,
        [AllowEmptyCollection()][Collections.Generic.HashSet[int]]$Seen
    )
    $rows=@(foreach($label in $Labels){
        $row=[regex]::Match($label,'^([0-9]+) · ([^\r\n]+)')
        if($row.Success){$row}
    })
    [pscustomobject]@{name='Native history shows confirmed hand rows';expected='True';actual=[string]($rows.Count-gt0);passed=($rows.Count-gt0)}
    foreach($row in $rows){
        $number=0
        if(![int]::TryParse($row.Groups[1].Value,[ref]$number)-or$number-lt1-or$number-gt$Contracts.Count){
            throw "Unexpected confirmed hand $($row.Groups[1].Value)"
        }
        $expected=$Contracts[$number-1].ToLowerInvariant()
        $actual=($row.Groups[2].Value -replace ' ×2$','').ToLowerInvariant()
        $passed=$expected-ceq$actual
        if($passed){[void]$Seen.Add($number)}
        [pscustomobject]@{name="Confirmed hand $number contract";expected=$expected;actual=$actual;passed=$passed}
    }
}

Export-ModuleMember -Function Wait-RentzUiHierarchy,Wait-RentzActorCheckpoint,Wait-RentzVisualCheckpoint,Get-RentzTapTarget,Get-RentzSystemUiWaitTarget,Start-RentzNextDeal,Get-RentzCompletedDealChecks,Get-RentzHistoryPageChecks,Get-RentzSeatFrames,Get-RentzTurnBadgeChecks
