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
    [Net.WebUtility]::HtmlDecode($Text).Replace('\','/') -replace '/+','/'
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

Export-ModuleMember -Function Get-RentzBuildRetention
