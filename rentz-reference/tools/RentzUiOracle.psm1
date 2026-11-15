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

Export-ModuleMember -Function Wait-RentzUiHierarchy,Wait-RentzActorCheckpoint,Wait-RentzVisualCheckpoint,Get-RentzTapTarget,Get-RentzSystemUiWaitTarget
