[CmdletBinding(DefaultParameterSetName='Plan')]
param(
    [Parameter(Mandatory)][string]$PlanFile,
    [Parameter(ParameterSetName='Plan',Mandatory)][string]$ControlReceipt,
    [Parameter(ParameterSetName='Plan',Mandatory)][string[]]$EvidenceRoot,
    [Parameter(ParameterSetName='Apply',Mandatory)][switch]$Apply,
    [Parameter(ParameterSetName='Apply',Mandatory)][string]$RunRoot,
    [Parameter(ParameterSetName='Apply',Mandatory)][scriptblock]$VerifyEvidence
)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot 'InfrastructureWorkspaces.psm1') -Force
Import-Module (Join-Path $PSScriptRoot 'BuildRetention.psm1') -Force
$data=Get-AtfDataRoot -ProjectRoot $project
Assert-AtfArtifactPath -Path $PlanFile -ProjectRoot $project
$activity={
    $git=@(git -C $project -c core.quotepath=false worktree list --porcelain)
    if($LASTEXITCODE){throw 'Could not enumerate current Git worktrees'}
    $trees=@($git|Where-Object {$_ -clike 'worktree *'}|ForEach-Object {$_.Substring(9)})
    if(!$trees.Count){throw 'No worktree was enumerated'}
    $commands=@(Get-CimInstance -ClassName Win32_Process -ErrorAction Stop |
        Where-Object CommandLine | ForEach-Object CommandLine)
    @{ActiveCommandLine=$commands;GitWorktree=$trees}
}.GetNewClosure()
if(!$Apply){
    if(Test-Path -LiteralPath $PlanFile){throw 'Use a new retention plan file'}
    $state=& $activity
    $plan=Get-RentzRuntimeRetention -DataRoot $data -ControlReceipt $ControlReceipt -EvidenceRoot $EvidenceRoot @state
    $plan | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $PlanFile -Encoding utf8NoBOM
    $plan
    return
}
Assert-AtfArtifactPath -Path $RunRoot -ProjectRoot $project
$plan=Get-Content -Raw -LiteralPath $PlanFile|ConvertFrom-Json
if([IO.Path]::GetFullPath([string]$plan.dataRoot)-ine$data){throw 'Retention plan belongs to another data root'}
Invoke-RentzRuntimeRetention -Plan $plan -RunRoot $RunRoot -Activity $activity -VerifyEvidence $VerifyEvidence
