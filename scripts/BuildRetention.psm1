Set-StrictMode -Version Latest
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1')

function Get-RentzPhysicalInventory {
    param([string]$Root)
    Assert-AtfPhysicalPath -Path $Root
    $pending=[Collections.Generic.Stack[string]]::new()
    $pending.Push($Root)
    while($pending.Count){
        foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force -ErrorAction Stop){
            $item
            if($item.PSIsContainer-and-not($item.Attributes-band[IO.FileAttributes]::ReparsePoint)){
                $pending.Push($item.FullName)
            }
        }
    }
}

function ConvertTo-RentzRetentionPath([string]$Text) {
    [Net.WebUtility]::HtmlDecode($Text).Replace('\','/') -replace '/{2,}','/'
}

function New-RentzRetentionReference([string]$Target,[string]$Workspace) {
    $path=[regex]::Escape((ConvertTo-RentzRetentionPath $Target))
    $parent=[regex]::Escape((ConvertTo-RentzRetentionPath $Workspace))
    $end='(?=$|[/\s;"''<>,)\]])'
    $parentEnd='(?=$|\r?\n|[;"''<>,)\]])'
    [regex]::new("(?:$path$end|$parent$parentEnd)",[Text.RegularExpressions.RegexOptions]'IgnoreCase, CultureInvariant')
}

function Get-RentzBuildRetention {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string[]]$Target,
        [Parameter(Mandatory)][string[]]$EvidenceRoot,
        [string[]]$ActiveCommandLine=@(),
        [string[]]$GitWorktree=@(),
        [hashtable]$WorkspaceByTarget=@{}
    )
    $rows=foreach($item in $Target){
        $path=[IO.Path]::GetFullPath($item)
        $workspace=if($WorkspaceByTarget.ContainsKey($path)){$WorkspaceByTarget[$path]}else{Split-Path -Parent $path}
        $reasons=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
        $evidence=[Collections.Generic.List[string]]::new()
        $ref=New-RentzRetentionReference $path $workspace
        try{
            if((Split-Path -Leaf $path)-cne'target'-or-not(Test-Path -LiteralPath $path -PathType Container)){
                $null=$reasons.Add('invalid-target')
            }else{
                $items=@(Get-RentzPhysicalInventory $path)
                if(@($items|Where-Object {$_.Attributes-band[IO.FileAttributes]::ReparsePoint}).Count){$null=$reasons.Add('linked-path')}
                if(@($items|Where-Object {$_.Name-eq'.git'-or(-not$_.PSIsContainer-and$_.Name-match'(?i)\.apk$|\.aab$|\.html$|^result\.json$|receipt.*\.json$|^TEST-.*\.xml$')}).Count){
                    $null=$reasons.Add('retained-output')
                }
            }
        }catch{$null=$reasons.Add('linked-or-unreadable-target')}
        $processRef = New-RentzRetentionReference $workspace $workspace
        foreach($command in $ActiveCommandLine){
            if($processRef.IsMatch((ConvertTo-RentzRetentionPath $command))){$null=$reasons.Add('process');break}
        }
        foreach($tree in $GitWorktree){
            $absolute=[IO.Path]::GetFullPath($tree).TrimEnd('\','/')
            if($path.StartsWith($absolute+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
               $absolute.StartsWith($path+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
               $absolute.Equals($path,[StringComparison]::OrdinalIgnoreCase)){
                $null=$reasons.Add('worktree');break
            }
        }
        [pscustomobject]@{path=$path;workspace=$workspace;reference=$ref;reasons=$reasons;evidence=$evidence}
    }
    $incomplete=[Collections.Generic.List[string]]::new()
    $scanned=0
    foreach($root in ($EvidenceRoot|Sort-Object -Unique)){
        try{
            Get-RentzPhysicalInventory ([IO.Path]::GetFullPath($root)) | ForEach-Object {
                $item=$_
                if($item.Attributes-band[IO.FileAttributes]::ReparsePoint){
                    $incomplete.Add($item.FullName)
                }elseif(-not$item.PSIsContainer-and($item.Extension-in@('.json','.xml','.classpath','.txt','.properties','.log','.md','.html','.csv','.yaml','.yml','.jsonl'))){
                    try{
                        if($item.Length-gt128MB){throw 'Evidence file exceeds the inventory limit'}
                        $text=ConvertTo-RentzRetentionPath ([IO.File]::ReadAllText($item.FullName))
                        $scanned++
                        foreach($row in $rows){
                            if($row.reference.IsMatch($text)){
                                $null=$row.reasons.Add('evidence')
                                $row.evidence.Add($item.FullName)
                            }
                        }
                    }catch{$incomplete.Add($item.FullName)}
                }
            }
        }catch{$incomplete.Add($root)}
    }
    foreach($row in $rows){
        if($incomplete.Count){$null=$row.reasons.Add('inventory-incomplete')}
        [pscustomobject]@{
            path=$row.path
            workspace=$row.workspace
            status=if($row.reasons.Count){'KEEP'}else{'REVIEW'}
            reasons=@($row.reasons|Sort-Object)
            evidence=@($row.evidence|Sort-Object -Unique)
            scannedFiles=$scanned
            unreadableOrLinkedEvidence=@($incomplete|Sort-Object -Unique)
        }
    }
}


function Test-RentzPathWithin([string]$Path,[string]$Parent) {
    $comparison=[StringComparison]::OrdinalIgnoreCase
    $Path.Equals($Parent,$comparison) -or $Path.StartsWith($Parent.TrimEnd('/','\')+[IO.Path]::DirectorySeparatorChar,$comparison)
}

function Get-RentzRuntimeSnapshot([string]$Path,[string]$Kind) {
    $inventory=[Collections.Generic.List[string]]::new()
    $bytes=0L
    foreach($entry in @(Get-RentzPhysicalInventory $Path | Sort-Object FullName -CaseSensitive)){
        if($entry.Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'linked-path'}
        $relative=[IO.Path]::GetRelativePath($Path,$entry.FullName).Replace('\','/')
        if($entry.Name-in@('.git','.atf-signing')-or $entry.Name-match'(?i)\.(aab|jks|keystore|p12|pfx|pem)$'){
            throw 'retained-output'
        }
        $mockDigests=$null
        if($entry.Name -match '(?i)\.apk$'){
            if($Kind -cne 'fixture' -or $entry.PSIsContainer){throw 'retained-output'}
            # The runtime controls write these two text payloads under APK names.
            $mockDigests=switch -CaseSensitive ($relative){
                'downloaded.apk' {
                    '2acc4d2fbb917519837e46e95bb54598c1cb795237074d92c233530697d25d13'
                    '1b32f7387323a7cbd13f071c3e2d0ba2288b2482101752904cb194e92fe89466'
                }
                'authenticated-RentzLab-0.11.0.apk' {'dc9e538f443bc1a4b049874211be9b0326b4a86b4abb03c2a868a7635723fee7'}
                default {throw 'retained-output'}
            }
        }
        if($Kind-ceq'cache'){
            if($entry.PSIsContainer-or$entry.Name-cne'.atf-j5-runtime.lock'-or$entry.Length-ne0){throw 'retained-output'}
            try{$handle=[IO.File]::Open($entry.FullName,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}
            catch{throw 'cache-in-use'}
            $handle.Dispose()
        }
        if($entry.PSIsContainer){$inventory.Add("D $relative");continue}
        $digest=(Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256 -ErrorAction Stop).Hash.ToLowerInvariant()
        if($mockDigests-and$digest-cnotin$mockDigests){throw 'retained-output'}
        $inventory.Add("F $digest $relative")
        $bytes+=$entry.Length
    }
    $hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($inventory-join [char]0)))).ToLowerInvariant()
    [pscustomobject]@{path=$Path;kind=$Kind;sha256=$hash;bytes=$bytes}
}

function Find-RentzRuntimeDependencies([string[]]$Roots,[string[]]$Candidates,[string]$OwnerReceipt,[regex[]]$References) {
    $found=[Collections.Generic.List[string]]::new()
    $unreadable=[Collections.Generic.List[string]]::new()
    $scanned=0
    $leafNames=@($Candidates|ForEach-Object {Split-Path -Leaf $_})
    foreach($root in $Roots){
        try{
            Assert-AtfPhysicalPath -Path $root
            $pending=[Collections.Generic.Stack[string]]::new()
            $pending.Push($root)
            while($pending.Count){
                foreach($item in Get-ChildItem -LiteralPath $pending.Pop() -Force -ErrorAction Stop){
                    $path=$item.FullName
                    if($path -ieq $OwnerReceipt -or $path -iin $Candidates){continue}
                    if($item.Attributes-band[IO.FileAttributes]::ReparsePoint){$unreadable.Add($path);continue}
                    if($item.PSIsContainer){$pending.Push($path);continue}
                    if($item.Extension -notin @('.json','.xml','.classpath','.txt','.properties','.log','.md','.html','.csv','.yaml','.yml','.jsonl')){continue}
                    try{
                        if($item.Length-gt128MB){throw 'Evidence exceeds inventory limit'}
                        $raw=[IO.File]::ReadAllText($path)
                        $scanned++
                        $decoded=[Net.WebUtility]::HtmlDecode($raw)
                        if(!($leafNames|Where-Object {$decoded.Contains($_,[StringComparison]::OrdinalIgnoreCase)}|Select-Object -First 1)){continue}
                        $text=ConvertTo-RentzRetentionPath $raw
                        foreach($ref in $References){
                            if($ref.IsMatch($text)){$found.Add($path);break}
                        }
                    }catch{$unreadable.Add($path)}
                }
            }
        }catch{$unreadable.Add($root)}
    }
    [pscustomobject]@{scanned=$scanned;evidence=@($found|Sort-Object -Unique);incomplete=@($unreadable|Sort-Object -Unique)}
}

function Get-RentzRuntimeRetention {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$DataRoot,
        [Parameter(Mandatory)][string]$ControlReceipt,
        [Parameter(Mandatory)][string[]]$EvidenceRoot,
        [string[]]$ActiveCommandLine=@(),
        [string[]]$GitWorktree=@()
    )
    $ErrorActionPreference='Stop'
    $data=[IO.Path]::GetFullPath($DataRoot)
    $receipt=[IO.Path]::GetFullPath($ControlReceipt)
    $roots=@($EvidenceRoot|ForEach-Object{[IO.Path]::GetFullPath($_)}|Sort-Object -Unique)
    $reasons=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $paths=[Collections.Generic.List[object]]::new()
    $receiptHash=''
    $dependencies=[pscustomobject]@{scanned=0;evidence=@();incomplete=@()}
    try{
        Assert-AtfPhysicalPath -Path $data
        Assert-AtfPhysicalPath -Path $receipt
        $owner=Split-Path -Parent $receipt
        if((Split-Path -Parent $owner)-ine (Join-Path $data 'infrastructure/governance') -or
           (Split-Path -Leaf $owner)-cnotmatch'^qualified-runtime-controls-[a-f0-9]{32}$' -or
           (Split-Path -Leaf $receipt)-cne'controls.json' -or (Get-Item -LiteralPath $receipt).Length-gt1MB){throw 'ownership'}
        $receiptBytes=[IO.File]::ReadAllBytes($receipt)
        $result=[Text.Encoding]::UTF8.GetString($receiptBytes)|ConvertFrom-Json -AsHashtable
        $receiptHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($receiptBytes)).ToLowerInvariant()
        if($result.status-cne'PASS'-or$result.failed-ne0-or$result.tests-lt1-or$result.results.Count-ne$result.tests-or
           @($result.results|Where-Object status -CNE PASS).Count-or
           $result.scope-cne'Byte fixtures, injected Java recorder and extracted publication block; no real JVM body, game, full native launcher or device'){throw 'ownership'}
        $fixture=[IO.Path]::GetFullPath([string]$result.fixture)
        $product=[IO.Path]::GetFullPath([string]$result.productFixtures)
        $fixtureName=Split-Path -Leaf $fixture
        if(!(Test-RentzPathWithin $fixture (Join-Path $data 'infrastructure/workspaces'))-or
           (Split-Path -Parent $fixture)-ine(Join-Path $data 'infrastructure/workspaces')-or $fixtureName-cnotmatch'^fixture-[a-f0-9]{32}$'-or
           (Split-Path -Parent $product)-ine(Join-Path $data 'components')-or (Split-Path -Leaf $product)-cnotmatch'^runtime-fixtures-[a-f0-9]{32}$'){throw 'ownership'}
        $descriptorPath=Join-Path $fixture '.atf-workspace.json'
        Assert-AtfPhysicalPath -Path $descriptorPath
        $descriptor=Get-Content -Raw -LiteralPath $descriptorPath|ConvertFrom-Json -AsHashtable
        if($descriptor.schemaVersion-ne1-or$descriptor.owner-cne'atf_j5'-or$descriptor.kind-cne'fixture'-or
           $fixtureName-cne("fixture-"+$descriptor.id)){throw 'ownership'}
        $locations=[ordered]@{}
        $locations[$fixture]='fixture'
        $locations[$product]='fixture-output'
        $caches=@($result['disposableCaches']|Where-Object {$_})
        if(!$result.Contains('disposableCaches')){
            # Legacy controls recorded synthetic repository paths in their per-case receipts.
            $caches=@(foreach($case in Get-ChildItem -LiteralPath $fixture -Directory -Force){
                if($case.Attributes-band[IO.FileAttributes]::ReparsePoint){continue}
                $caseReceipt=Join-Path $case.FullName 'component/result.json'
                if(!(Test-Path -LiteralPath $caseReceipt -PathType Leaf)){continue}
                Assert-AtfPhysicalPath -Path $caseReceipt
                if((Get-Item -LiteralPath $caseReceipt).Length-gt1MB){throw 'ownership'}
                $caseResult=Get-Content -Raw -LiteralPath $caseReceipt|ConvertFrom-Json -AsHashtable
                $candidate=[string]$caseResult['repository']
                if($candidate-and(Test-Path -LiteralPath $candidate -PathType Container)){$candidate}
            })
        }
        foreach($cache in $caches){
            $absolute=[IO.Path]::GetFullPath([string]$cache)
            if((Split-Path -Parent $absolute)-ine(Join-Path $data 'infrastructure/cache')-or
                (Split-Path -Leaf $absolute)-cnotmatch'^runtime-fixture-[a-f0-9]{32}$'){throw 'ownership'}
            $locations[$absolute]='cache'
        }
        foreach($entry in $locations.GetEnumerator()){
            try{$paths.Add((Get-RentzRuntimeSnapshot $entry.Key $entry.Value))}
            catch{
                $reason=$_.Exception.Message
                if($reason -cnotin @('linked-path','retained-output','cache-in-use')){$reason='linked-or-unreadable-target'}
                $null=$reasons.Add($reason)
            }
        }
        $keys=[string[]]@($locations.Keys)
        foreach($root in $roots){foreach($target in $keys){if(Test-RentzPathWithin $root $target){throw 'inventory-incomplete'}}}
        $references=@(foreach($target in $keys){New-RentzRetentionReference $target $target})
        foreach($line in $ActiveCommandLine){
            $text=ConvertTo-RentzRetentionPath $line
            if(@($references|Where-Object {$_.IsMatch($text)}).Count){$null=$reasons.Add('process');break}
        }
        foreach($tree in $GitWorktree){
            $path=[IO.Path]::GetFullPath($tree)
            foreach($target in $keys){
                if((Test-RentzPathWithin $path $target)-or(Test-RentzPathWithin $target $path)){$null=$reasons.Add('worktree');break}
            }
        }
        $dependencies=Find-RentzRuntimeDependencies $roots $keys $receipt $references
        if($dependencies.evidence.Count){$null=$reasons.Add('evidence')}
        if($dependencies.incomplete.Count){$null=$reasons.Add('inventory-incomplete')}
    }catch{
        $reason=$_.Exception.Message
        $null=$reasons.Add($(if($reason-ceq'inventory-incomplete'){$reason}else{'ownership'}))
    }
    [pscustomobject]@{
        status=if($reasons.Count){'KEEP'}else{'REGENERABLE'}
        dataRoot=$data;controlReceipt=$receipt;controlReceiptSha256=$receiptHash;evidenceRoot=$roots
        paths=@($paths.ToArray());reasons=@($reasons|Sort-Object);dependencies=$dependencies
    }
}

function Invoke-RentzRuntimeRetention {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]$Plan,
        [Parameter(Mandatory)][string]$RunRoot,
        [Parameter(Mandatory)][scriptblock]$Activity,
        [Parameter(Mandatory)][scriptblock]$VerifyEvidence
    )
    $ErrorActionPreference='Stop'
    if($Plan.status-cne'REGENERABLE'){throw 'Retention requires an eligible reviewed plan'}
    $run=[IO.Path]::GetFullPath($RunRoot)
    $validation=Join-Path $Plan.dataRoot 'validation'
    if(!(Test-RentzPathWithin $run $validation)-or$run-ieq$validation-or(Test-Path -LiteralPath $run)){throw 'Use a fresh retention run under validation'}
    Assert-AtfPhysicalPath -Path $run
    $activityState=& $Activity
    if($activityState -isnot [hashtable]-or!$activityState.ContainsKey('ActiveCommandLine')-or!$activityState.ContainsKey('GitWorktree')){throw 'Retention activity census is incomplete'}
    $fresh=Get-RentzRuntimeRetention -DataRoot $Plan.dataRoot -ControlReceipt $Plan.controlReceipt -EvidenceRoot $Plan.evidenceRoot @activityState
    if($fresh.status-cne'REGENERABLE'-or$fresh.controlReceiptSha256-cne$Plan.controlReceiptSha256-or
       ($fresh.paths|ConvertTo-Json -Compress)-cne($Plan.paths|ConvertTo-Json -Compress)){
        throw "Retention plan drift: $($fresh.reasons -join ', ')"
    }
    $quarantine=Join-Path $run 'quarantine'
    New-Item -ItemType Directory -Path $quarantine | Out-Null
    $moves=@(foreach($entry in $fresh.paths){[pscustomobject]@{
        original=$entry.path;quarantine=(Join-Path $quarantine (Split-Path -Leaf $entry.path));sha256=$entry.sha256;bytes=$entry.bytes
    }})
    $receipt=[ordered]@{status='PLANNED';controlReceipt=$fresh.controlReceipt;controlReceiptSha256=$fresh.controlReceiptSha256;paths=$moves}
    $receipt|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $run 'plan.json')
    $moved=[Collections.Generic.List[object]]::new()
    try{
        for($index=0;$index-lt$moves.Count;$index++){
            $entry=$moves[$index]
            if((Get-RentzRuntimeSnapshot $entry.original $fresh.paths[$index].kind).sha256-cne$entry.sha256){throw 'Retention path drift before move'}
            Assert-AtfPhysicalPath -Path $entry.quarantine
            if((Split-Path -Parent $entry.quarantine)-cne$quarantine){throw 'Retention quarantine path escaped'}
            [IO.Directory]::Move($entry.original,$entry.quarantine)
            $moved.Add($entry)
            if((Get-RentzRuntimeSnapshot $entry.quarantine $fresh.paths[$index].kind).sha256-cne$entry.sha256){
                throw 'Retention path drift during move'
            }
        }
        $verification=& $VerifyEvidence
        if($verification -isnot [bool]-or!$verification){throw 'Retention evidence reread failed'}
        $receipt.status='VERIFIED'
    }catch{
        $receipt.error=$_.Exception.Message
        $rollback=[Collections.Generic.List[string]]::new()
        for($index=$moved.Count-1;$index-ge0;$index--){
            $entry=$moved[$index]
            try{
                Assert-AtfPhysicalPath -Path $entry.original
                Assert-AtfPhysicalPath -Path $entry.quarantine
                if(Test-Path -LiteralPath $entry.original){throw 'Original path was recreated'}
                [IO.Directory]::Move($entry.quarantine,$entry.original)
            }catch{$rollback.Add($entry.original)}
        }
        $receipt.status=if($rollback.Count){'ROLLBACK_FAILED'}else{'ROLLED_BACK'}
        $receipt.rollbackFailures=@($rollback)
    }
    [pscustomobject]$receipt|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $run 'result.json')
    if($receipt.status-cne'VERIFIED'){throw "$($receipt.status): $($receipt.error)"}
    [pscustomobject]$receipt
}

Export-ModuleMember -Function Get-RentzBuildRetention,Get-RentzRuntimeRetention,Invoke-RentzRuntimeRetention
